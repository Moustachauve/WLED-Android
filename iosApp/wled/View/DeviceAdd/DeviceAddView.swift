import SwiftUI

struct DeviceAddView: View {
    @Environment(\.managedObjectContext) private var viewContext
    @Environment(\.dismiss) var dismiss

    @ObservedObject private var viewModel = DeviceAddViewModel()

    var body: some View {
        NavigationView {
            VStack {
                switch viewModel.currentStep {
                case .form(let errorMessage):
                    DeviceAddStep1FormView(
                        viewModel: viewModel,
                        errorMessage: errorMessage,
                    )
                case .adding:
                    DeviceAddStep2LoadingView(address: viewModel.address)
                case .success(let device):
                    DeviceAddStep3Success(device: device)
                }
                Spacer()
            }
            .padding()
            .animation(.easeInOut, value: viewModel.currentStep)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") {
                        dismiss()
                    }
                }
                if viewModel.currentStep.isForm {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Add", systemImage: "checkmark") {
                            withAnimation {
                                viewModel.submitCreateDevice()
                            }
                        }
                    }
                }
            }
            .navigationTitle("New Device")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
    }
}

// MARK: - Step 1: Form

struct DeviceAddStep1FormView: View {

    @ObservedObject var viewModel: DeviceAddViewModel
    @FocusState private var focusedField: Field?

    let errorMessage: String
    let state = DeviceAddViewModel.Step.self

    var body: some View {
        VStack(alignment: .leading) {
            Text("IP Address or URL")
            TextField("IP Address or URL", text: $viewModel.address)
                .keyboardType(.URL)
                .submitLabel(.done)
                .textFieldStyle(.roundedBorder)
                .focused($focusedField, equals: .address)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(
                            errorMessage.isEmpty ? Color.clear : Color.red
                        )
                )
                .onSubmit {
                    withAnimation {
                        viewModel.submitCreateDevice()
                    }
                }
            if !errorMessage.isEmpty {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(Font.caption.bold())
            }
        }
        .onAppear {
            focusedField = .address
        }
    }

    enum Field: Hashable {
        case address
    }
}

// MARK: - Step 2: Adding, Loading indicator

struct DeviceAddStep2LoadingView: View {
    let address: String

    var body: some View {
        ProgressView()
            .controlSize(ControlSize.large)
            .padding()
        Text("Adding \(address)")
    }
}

// MARK: - Step 3: Success

struct DeviceAddStep3Success: View {
    let device: Device

    var body: some View {
        Image(systemName: "checkmark.seal")
            .font(.system(size: 50))
            .foregroundStyle(.green)
            .padding()
        Text("\(device.displayName) was added")
    }
}

#Preview {
    DeviceAddView()
}
