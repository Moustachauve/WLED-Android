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

    @Test func didStartProvisionalNavigationResetsIsErrorAndSetsIsLoading() {
        var isError = true
        var isLoading = false
        let sut = makeCoordinator(
            isError: Binding(get: { isError }, set: { isError = $0 }),
            isLoading: Binding(get: { isLoading }, set: { isLoading = $0 })
        )

        sut.webView(WKWebView(), didStartProvisionalNavigation: nil)

        #expect(isError == false)
        #expect(isLoading == true)
    }

    @Test func didFinishNavigationSetsIsLoadingFalse() {
        var isLoading = true
        let sut = makeCoordinator(
            isLoading: Binding(get: { isLoading }, set: { isLoading = $0 })
        )

        sut.webView(WKWebView(), didFinish: nil)

        #expect(isLoading == false)
    }

    @Test(arguments: [
        NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut),
        NSError(domain: WKErrorDomain, code: WLED.WebView.Coordinator.frameLoadInterruptedByPolicyChangeErrorCode)
    ])
    func navigationFailureOrDownloadInterruptionClearsIsLoading(error: NSError) {
        var isLoading = true
        let sut = makeCoordinator(
            isLoading: Binding(get: { isLoading }, set: { isLoading = $0 })
        )

        sut.webView(WKWebView(), didFailProvisionalNavigation: nil, withError: error)

        #expect(isLoading == false)
    }

    // MARK: - Helpers

    private func makeCoordinator(
        isError: Binding<Bool> = .constant(false),
        isLoading: Binding<Bool> = .constant(false)
    ) -> WLED.WebView.Coordinator {
        let webView = WLED.WebView(
            url: URL(string: "http://192.168.1.50"),
            reload: .constant(false),
            isError: isError,
            isLoading: isLoading,
            downloadCompleted: { _ in }
        )
        return webView.makeCoordinator()
    }
}
