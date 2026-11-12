package com.autoparts_maven.autoparts_maven.delivery;

/** The address district is in no active delivery region, so the store does not deliver there yet. */
public class DistrictNotServedException extends RuntimeException {

    private final String district;

    public DistrictNotServedException(String district) {
        super("We do not deliver to " + district + " yet");
        this.district = district;
    }

    public String getDistrict() {
        return district;
    }
}
