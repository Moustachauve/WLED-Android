import Foundation
import SwiftUI
import Testing
import WebKit
@testable import WLED

@MainActor
struct WebViewCoordinatorTests {

    @Test func didFailProvisionalNavigationSetsIsErrorTrue() {
        var isError = false
        let sut = makeCoordinator(isError: Binding(get: { isError }, set: { isError = $0 }))
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorCannotConnectToHost)

        sut.webView(WKWebView(), didFailProvisionalNavigation: nil, withError: error)

        #expect(isError == true)
    }

    @Test func didFailNavigationSetsIsErrorTrue() {
        var isError = false
        let sut = makeCoordinator(isError: Binding(get: { isError }, set: { isError = $0 }))
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut)

        sut.webView(WKWebView(), didFail: nil, withError: error)

        #expect(isError == true)
    }

    @Test(arguments: [
        NSError(domain: NSURLErrorDomain, code: NSURLErrorCancelled),
        NSError(domain: WKErrorDomain, code: WLED.WebView.Coordinator.frameLoadInterruptedByPolicyChangeErrorCode)
    ])
    func nonFatalNavigationInterruptionDoesNotSetIsError(error: NSError) {
        var isError = false
        let sut = makeCoordinator(isError: Binding(get: { isError }, set: { isError = $0 }))

        sut.webView(WKWebView(), didFailProvisionalNavigation: nil, withError: error)

        #expect(isError == false)
    }

    @Test func didStartProvisionalNavigationResetsIsErrorFalse() {
        var isError = true
        let sut = makeCoordinator(isError: Binding(get: { isError }, set: { isError = $0 }))

        sut.webView(WKWebView(), didStartProvisionalNavigation: nil)

        #expect(isError == false)
    }

    // MARK: - Helpers

    private func makeCoordinator(isError: Binding<Bool>) -> WLED.WebView.Coordinator {
        let webView = WLED.WebView(
            url: URL(string: "http://192.168.1.50"),
            reload: .constant(false),
            isError: isError,
            downloadCompleted: { _ in }
        )
        return webView.makeCoordinator()
    }
}
