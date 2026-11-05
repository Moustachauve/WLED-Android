import Foundation
import Shared
import Synchronization

extension DeviceApiFactory {
    static let stubbed = DeviceApiFactory.companion.create { configuration in
        configuration.protocolClasses = [StubURLProtocol.self]
    }
}

/// `URLProtocol` that serves canned responses so networking code can be tested without a real device.
///
/// Stubs are registered per host so that tests running in parallel never see each other's responses.
/// Install it on a session through `URLSessionConfiguration.protocolClasses`.
final class StubURLProtocol: URLProtocol, @unchecked Sendable {

    enum Stub: Sendable {
        case http(statusCode: Int, body: String, contentType: String = "application/json")
        case failure(URLError.Code)
        /// Never answers, leaving the request pending until it is cancelled.
        case never
    }

    private struct State: Sendable {
        var stubs: [String: [Stub]] = [:]
        var recordedRequests: [String: [URLRequest]] = [:]
    }

    private static let state = Mutex(State())

    static func register(_ stub: Stub, for host: String) {
        register([stub], for: host)
    }

    static func register(_ stubs: [Stub], for host: String) {
        state.withLock { $0.stubs[host] = stubs }
    }

    static func requests(for host: String) -> [URLRequest] {
        state.withLock { $0.recordedRequests[host] ?? [] }
    }

    private static func stub(recording request: URLRequest) -> Stub? {
        guard let host = request.url?.host else { return nil }
        let recorded = materializedRequest(request)
        return state.withLock {
            $0.recordedRequests[host, default: []].append(recorded)
            guard var hostStubs = $0.stubs[host], let next = hostStubs.first else {
                return nil
            }
            if hostStubs.count > 1 {
                hostStubs.removeFirst()
                $0.stubs[host] = hostStubs
            }
            return next
        }
    }

    private static func materializedRequest(_ request: URLRequest) -> URLRequest {
        guard request.httpBody == nil, let stream = request.httpBodyStream else { return request }
        var copy = request
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: buffer.count)
            guard read > 0 else { break }
            data.append(contentsOf: buffer[..<read])
        }
        copy.httpBody = data
        return copy
    }

    // MARK: - URLProtocol

    override static func canInit(with request: URLRequest) -> Bool { true }

    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let stub = Self.stub(recording: request) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotFindHost))
            return
        }

        switch stub {
        case let .http(statusCode, body, contentType):
            guard let response = HTTPURLResponse(
                url: url,
                statusCode: statusCode,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": contentType]
            ) else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(body.utf8))
            client?.urlProtocolDidFinishLoading(self)
        case let .failure(code):
            client?.urlProtocol(self, didFailWithError: URLError(code))
        case .never:
            break
        }
    }

    override func stopLoading() {}
}
