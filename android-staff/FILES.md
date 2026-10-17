# Created files

All implementation changes are inside `android-staff/`. Existing application source, migrations and production configuration were preserved. Generated build outputs, caches and local SDK paths are ignored.

```text
.gitignore
FILES.md
README.md
VERIFICATION.md
app/build.gradle
app/proguard-rules.pro
app/src/androidTest/java/au/com/nakornthai/staffapp/FixtureApplication.java
app/src/androidTest/java/au/com/nakornthai/staffapp/SecureStorageTest.java
app/src/androidTest/java/au/com/nakornthai/staffapp/StaffUiTest.java
app/src/androidTest/java/au/com/nakornthai/staffapp/TestRunner.java
app/src/debug/res/xml/network_security_config.xml
app/src/main/AndroidManifest.xml
app/src/main/java/au/com/nakornthai/staffapp/analytics/ui/AnalyticsFragment.java
app/src/main/java/au/com/nakornthai/staffapp/app/AppGraph.java
app/src/main/java/au/com/nakornthai/staffapp/app/MainActivity.java
app/src/main/java/au/com/nakornthai/staffapp/app/StaffApplication.java
app/src/main/java/au/com/nakornthai/staffapp/app/StaffViewModelFactory.java
app/src/main/java/au/com/nakornthai/staffapp/identity/data/IdentityRepository.java
app/src/main/java/au/com/nakornthai/staffapp/identity/data/Session.java
app/src/main/java/au/com/nakornthai/staffapp/identity/ui/AuthViewModel.java
app/src/main/java/au/com/nakornthai/staffapp/identity/ui/LoginFragment.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/data/ApiOrderRepository.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/data/OrderRepository.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/model/Order.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/model/OrderDraft.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/monitoring/BoardState.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/monitoring/NewOrderTracker.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/monitoring/OrderMonitor.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrderActionDialog.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrderAdapter.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrderDetailDialog.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrderPresentation.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrdersFragment.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrdersViewModel.java
app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/RetainedDraftsDialog.java
app/src/main/java/au/com/nakornthai/staffapp/reservation/ui/ReservationsFragment.java
app/src/main/java/au/com/nakornthai/staffapp/shared/navigation/ManageSheet.java
app/src/main/java/au/com/nakornthai/staffapp/shared/network/ApiFailure.java
app/src/main/java/au/com/nakornthai/staffapp/shared/network/HttpResult.java
app/src/main/java/au/com/nakornthai/staffapp/shared/network/SessionCookieJar.java
app/src/main/java/au/com/nakornthai/staffapp/shared/network/StaffApi.java
app/src/main/java/au/com/nakornthai/staffapp/shared/security/CredentialStore.java
app/src/main/java/au/com/nakornthai/staffapp/shared/security/KeystoreCredentialStore.java
app/src/main/res/color/staff_nav_background.xml
app/src/main/res/color/staff_nav_foreground.xml
app/src/main/res/drawable/board_column.xml
app/src/main/res/drawable/count_badge.xml
app/src/main/res/drawable/ic_analytics.xml
app/src/main/res/drawable/ic_home.xml
app/src/main/res/drawable/ic_menu.xml
app/src/main/res/drawable/ic_orders.xml
app/src/main/res/drawable/ic_reservations.xml
app/src/main/res/drawable/nakorn_logo.png
app/src/main/res/layout-sw600dp/fragment_login.xml
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/dialog_order.xml
app/src/main/res/layout/dialog_order_action.xml
app/src/main/res/layout/fragment_login.xml
app/src/main/res/layout/fragment_orders.xml
app/src/main/res/layout/fragment_placeholder.xml
app/src/main/res/layout/item_order.xml
app/src/main/res/layout/sheet_manage.xml
app/src/main/res/layout/view_order_column.xml
app/src/main/res/menu/footer.xml
app/src/main/res/navigation/staff_navigation.xml
app/src/main/res/values-sw600dp/dimens.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/dimens.xml
app/src/main/res/values/ids.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/xml/data_extraction_rules.xml
app/src/main/res/xml/network_security_config.xml
app/src/test/java/au/com/nakornthai/staffapp/ApiContractTest.java
app/src/test/java/au/com/nakornthai/staffapp/OrderMonitorTest.java
app/src/test/java/au/com/nakornthai/staffapp/OrderWorkflowTest.java
app/src/test/java/au/com/nakornthai/staffapp/OrdersViewModelTest.java
build.gradle
gradle.properties
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
settings.gradle
app/src/androidTest/java/au/com/nakornthai/staffapp/PrinterUiTest.java
app/src/main/java/au/com/nakornthai/staffapp/printing/connection/AndroidPrinterConnections.java
app/src/main/java/au/com/nakornthai/staffapp/printing/connection/BluetoothPrinterConnection.java
app/src/main/java/au/com/nakornthai/staffapp/printing/connection/NetworkPrinterConnection.java
app/src/main/java/au/com/nakornthai/staffapp/printing/connection/PrinterConnection.java
app/src/main/java/au/com/nakornthai/staffapp/printing/connection/PrinterConnectionManager.java
app/src/main/java/au/com/nakornthai/staffapp/printing/data/PrinterConfigurationRepository.java
app/src/main/java/au/com/nakornthai/staffapp/printing/diagnostics/PrinterDiagnostics.java
app/src/main/java/au/com/nakornthai/staffapp/printing/discovery/BluetoothPrinterDiscovery.java
app/src/main/java/au/com/nakornthai/staffapp/printing/discovery/NetworkPrinterDiscovery.java
app/src/main/java/au/com/nakornthai/staffapp/printing/discovery/PrinterDiscovery.java
app/src/main/java/au/com/nakornthai/staffapp/printing/driver/EscPosPrinterDriver.java
app/src/main/java/au/com/nakornthai/staffapp/printing/driver/PrinterDriver.java
app/src/main/java/au/com/nakornthai/staffapp/printing/driver/PrinterDriverRegistry.java
app/src/main/java/au/com/nakornthai/staffapp/printing/driver/UnsupportedPrinterDriver.java
app/src/main/java/au/com/nakornthai/staffapp/printing/formatting/TestDocketFormatter.java
app/src/main/java/au/com/nakornthai/staffapp/printing/model/PrinterCapabilities.java
app/src/main/java/au/com/nakornthai/staffapp/printing/model/PrinterProfile.java
app/src/main/java/au/com/nakornthai/staffapp/printing/ui/PrinterManagementFragment.java
app/src/main/java/au/com/nakornthai/staffapp/printing/ui/PrinterManagementViewModel.java
app/src/main/res/layout/fragment_printers.xml
app/src/test/java/au/com/nakornthai/staffapp/PrinterProvisioningTest.java
```

## Phase A modifications to existing files

- `app/src/main/AndroidManifest.xml`: contextual Bluetooth permissions and optional hardware declarations; backend network policy unchanged.
- `app/src/main/java/au/com/nakornthai/staffapp/app/MainActivity.java`: ADMIN guard for Printers navigation only.
- `app/src/main/java/au/com/nakornthai/staffapp/shared/navigation/ManageSheet.java`: ADMIN-only Printers menu item.
- `app/src/main/res/layout/sheet_manage.xml`, `res/navigation/staff_navigation.xml`, `res/values/strings.xml`: local printer destination and provisioning labels.
- `app/src/androidTest/java/au/com/nakornthai/staffapp/FixtureApplication.java`: isolated ADMIN test identity support; no production credentials/API traffic.
- `README.md`, `FILES.md`, `VERIFICATION.md`: Phase A usage, implementation inventory, verification and hardware limitations.

The Phase A additions above implement profile/configuration storage, driver capabilities/registry, ASCII test formatting, bounded Classic SPP/LAN connections, cancellable Classic/BLE/NSD discovery, isolated diagnostics and an Activity-scoped provisioning ViewModel/Fragment. No existing order/authentication/payment implementation was changed.
