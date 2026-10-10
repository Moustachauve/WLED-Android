import SwiftUI
@preconcurrency import WebKit

struct WebView: UIViewRepresentable {

    var url: URL?
    @Binding var reload: Bool
    private let downloadCompleted: (URL) -> Void

    init(url: URL?, reload: Binding<Bool>, downloadCompleted: @escaping (URL) -> Void) {
        self.url = url
        _reload = reload
        self.downloadCompleted = downloadCompleted
    }

    func makeUIView(context: Context) -> WKWebView {
        print("WebView makeUIView")
        let webView = WKWebView()

        webView.customUserAgent = "wled-native/1"
        webView.isOpaque = false
        webView.backgroundColor = UIColor.clear
        webView.uiDelegate = context.coordinator
        webView.navigationDelegate = context.coordinator
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        print("WebView updateUIView, current url: \(url, default: "[unknown]")")
        webView.underPageBackgroundColor = .systemBackground

        if let url = self.url, url != context.coordinator.lastLoadedUrl {
            context.coordinator.lastLoadedUrl = url
            let request = URLRequest(url: url)
            webView.load(request)
        }

        if reload {
            webView.reload()
            DispatchQueue.main.async {
                reload = false
            }
        }
    }

    func onDownloadCompleted(_ filePathDestination: URL) {
        downloadCompleted(filePathDestination)
    }

    @MainActor
    class Coordinator: NSObject, WKUIDelegate, WKNavigationDelegate, WKDownloadDelegate {
        var parent: WebView
        var lastLoadedUrl: URL?
        private var filePathDestination: URL?

        init(_ parent: WebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            let langStr = {
                switch Locale.current.language.languageCode?.identifier {
                case "fr":
                    return "fr"
                default:
                    return "en"
                }
            }()

            guard let htmlPath = Bundle.main.path(forResource: "errorPage.\(langStr)", ofType: "html") else { return }
            let htmlUrl = URL(fileURLWithPath: htmlPath, isDirectory: false)
            webView.loadFileURL(htmlUrl, allowingReadAccessTo: htmlUrl)
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, preferences: WKWebpagePreferences, decisionHandler: @escaping @MainActor (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
            if navigationAction.shouldPerformDownload {
                decisionHandler(.download, preferences)
            } else {
                decisionHandler(.allow, preferences)
            }
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping @MainActor (WKNavigationResponsePolicy) -> Void) {
            if navigationResponse.canShowMIMEType {
                decisionHandler(.allow)
            } else {
                decisionHandler(.download)
            }
        }

        func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String, completionHandler: @escaping @MainActor @Sendable (URL?) -> Void) {
            filePathDestination = getDownloadPath(suggestedFilename as NSString)
            Task { @MainActor in
                completionHandler(filePathDestination)
            }
        }

        func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
            download.delegate = self
        }

        func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
            download.delegate = self
        }

        func download(_ download: WKDownload, didFailWithError: Error, resumeData: Data?) {
            print("Failed to download: \(didFailWithError)")
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
            }

            return nil
        }

        func downloadDidFinish(_ download: WKDownload) {
            guard let filePathDestination = filePathDestination else {
                return
            }
            parent.onDownloadCompleted(filePathDestination)
            cleanUp()
        }

        private func getDownloadPath(_ suggestedFilename: NSString, _ counter: Int = 0) -> URL? {
            do {
                guard let downloadDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
                    print("no document path")
                    return nil
                }
                try FileManager.default.createDirectory(at: downloadDirectory, withIntermediateDirectories: true)

                // Add "(x)" in case the file already exists
                let pathExtension = suggestedFilename.pathExtension
                let pathPrefix = suggestedFilename.deletingPathExtension
                let counterSuffix = counter > 0 ? "(\(counter))" : ""
                let fileName = "\(pathPrefix)\(counterSuffix).\(pathExtension)"

                let path = downloadDirectory.appendingPathComponent(fileName)
                if FileManager.default.fileExists(atPath: path.path) {
                    return getDownloadPath(suggestedFilename, counter + 1)
                }

                return path
            } catch {
                print(error)
                return nil
            }
        }

        private func cleanUp() {
            filePathDestination = nil
        }

        // MARK: - WKUIDelegate JavaScript Alerts (Async)

        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo) async {
            await withCheckedContinuation { continuation in
                var alertStyle = UIAlertController.Style.actionSheet
                if UIDevice.current.userInterfaceIdiom == .pad {
                    alertStyle = UIAlertController.Style.alert
                }
                let alertController = UIAlertController(title: nil, message: message, preferredStyle: alertStyle)
                alertController.addAction(
                    UIAlertAction(title: "OK", style: .default, handler: { (_) in
                        continuation.resume()
                    })
                )
                if let controller = self.topMostViewController() {
                    controller.present(alertController, animated: true, completion: nil)
                } else {
                    // Resume if we can't present, to avoid hanging the webview
                    continuation.resume()
                }
            }
        }

        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo) async -> Bool {
            await withCheckedContinuation { continuation in
                var alertStyle = UIAlertController.Style.actionSheet
                if UIDevice.current.userInterfaceIdiom == .pad {
                    alertStyle = UIAlertController.Style.alert
                }
                let alertController = UIAlertController(title: nil, message: message, preferredStyle: alertStyle)
                alertController.addAction(
                    UIAlertAction(title: "OK", style: .default, handler: { (_) in
                        continuation.resume(returning: true)
                    })
                )
                alertController.addAction(
                    UIAlertAction(title: "Cancel", style: .default, handler: { (_) in
                        continuation.resume(returning: false)
                    })
                )

                if let controller = self.topMostViewController() {
                    controller.present(alertController, animated: true, completion: nil)
                } else {
                    continuation.resume(returning: false)
                }
            }
        }

        func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo) async -> String? {
            await withCheckedContinuation { continuation in
                var alertStyle = UIAlertController.Style.actionSheet
                if UIDevice.current.userInterfaceIdiom == .pad {
                    alertStyle = UIAlertController.Style.alert
                }
                let alertController = UIAlertController(title: nil, message: prompt, preferredStyle: alertStyle)

                alertController.addTextField { (textField) in
                    textField.text = defaultText
                }

                alertController.addAction(UIAlertAction(title: "OK", style: .default, handler: { (_) in
                    if let text = alertController.textFields?.first?.text {
                        continuation.resume(returning: text)
                    } else {
                        continuation.resume(returning: defaultText)
                    }
                }))

                alertController.addAction(UIAlertAction(title: "Cancel", style: .default, handler: { (_) in
                    continuation.resume(returning: nil)
                }))

                if let controller = self.topMostViewController() {
                    controller.present(alertController, animated: true, completion: nil)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }

        private func topMostViewController() -> UIViewController? {
            guard let rootController = keyWindow()?.rootViewController else {
                return nil
            }
            return topMostViewController(for: rootController)
        }

        private func keyWindow() -> UIWindow? {
            return UIApplication.shared.connectedScenes
                .filter {$0.activationState == .foregroundActive}
                .compactMap {$0 as? UIWindowScene}
                .first?.windows.filter {$0.isKeyWindow}.first
        }

        private func topMostViewController(for controller: UIViewController) -> UIViewController {
            if let presentedController = controller.presentedViewController {
                return topMostViewController(for: presentedController)
            } else if let navigationController = controller as? UINavigationController {
                guard let topController = navigationController.topViewController else {
                    return navigationController
                }
                return topMostViewController(for: topController)
            } else if let tabController = controller as? UITabBarController {
                guard let topController = tabController.selectedViewController else {
                    return tabController
                }
                return topMostViewController(for: topController)
            }
            return controller
        }

    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
}
