import Foundation

/// `URLProtocol` that serves canned responses so networking code can be tested without a real device.
///
/// Stubs are registered per host so that tests running in parallel never see each other's responses.
/// Install it on a session through `URLSessionConfiguration.protocolClasses`.
final class StubURLProtocol: URLProtocol, @unchecked Sendable {

    enum Stub {
        case http(statusCode: Int, body: String, contentType: String = "application/json")
        case failure(URLError.Code)
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var stubs: [String: Stub] = [:]
    nonisolated(unsafe) private static var recordedRequests: [String: [URLRequest]] = [:]

    static func register(_ stub: Stub, for host: String) {
        lock.withLock { stubs[host] = stub }
    }

    static func unregister(host: String) {
        lock.withLock {
            stubs[host] = nil
            recordedRequests[host] = nil
        }
    }

    static func requests(for host: String) -> [URLRequest] {
        lock.withLock { recordedRequests[host] ?? [] }
    }

    private static func stub(recording request: URLRequest) -> Stub? {
        guard let host = request.url?.host else { return nil }
        return lock.withLock {
            recordedRequests[host, default: []].append(request)
            return stubs[host]
        }
    }

    // MARK: - URLProtocol

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let stub = Self.stub(recording: request) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotFindHost))
            return
        }

        switch stub {
        case let .http(statusCode, body, contentType):
            let response = HTTPURLResponse(
                url: url,
                statusCode: statusCode,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": contentType]
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(body.utf8))
            client?.urlProtocolDidFinishLoading(self)
        case let .failure(code):
            client?.urlProtocol(self, didFailWithError: URLError(code))
        }
    }

    override func stopLoading() {}
}
