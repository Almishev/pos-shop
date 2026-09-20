package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.LicenseStatusResponse;

public interface LicenseService {

    /**
     * Throws 403 if license is required and not active.
     */
    void assertLicenseActive();

    /**
     * Current license status (for diagnostics). Does not throw.
     */
    LicenseStatusResponse getStatus();
}
