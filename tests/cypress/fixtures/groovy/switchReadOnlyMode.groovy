import org.jahia.settings.readonlymode.ReadOnlyModeController

// Switches the platform full read-only mode. switchReadOnlyMode is synchronous and
// idempotent when the controller is already in the target state. The final status is
// verified here because cy.executeGroovy only surfaces installed/failed, not the
// script return value: a mismatch must throw so the caller sees ".failed".
def controller = ReadOnlyModeController.getInstance()
def enable = "__ENABLE__".toBoolean()
controller.switchReadOnlyMode(enable)
def status = controller.getReadOnlyStatus()
def expected = enable ? "ON" : "OFF"
if (status.name() != expected) {
    throw new IllegalStateException("Read-only switch did not reach " + expected + ", status is " + status)
}
return status.name()
