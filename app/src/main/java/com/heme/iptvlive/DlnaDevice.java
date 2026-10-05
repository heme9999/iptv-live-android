package com.heme.iptvlive;

import java.util.Objects;

public final class DlnaDevice {
    public final String name;
    public final String ip;
    public final String locationUrl;
    public final String controlUrl;
    public final String serviceType;

    public DlnaDevice(String name, String ip, String locationUrl, String controlUrl, String serviceType) {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "局域网电视设备";
        this.ip = ip != null ? ip : "";
        this.locationUrl = locationUrl;
        this.controlUrl = controlUrl;
        this.serviceType = serviceType != null ? serviceType : "urn:schemas-upnp-org:service:AVTransport:1";
    }

    public String getFriendlyName() {
        return name;
    }

    public String getIpAddress() {
        return ip;
    }

    public String getModelName() {
        return "DLNA 设备";
    }

    public String getControlUrl() {
        return controlUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DlnaDevice that = (DlnaDevice) o;
        return Objects.equals(locationUrl, that.locationUrl) || (Objects.equals(ip, that.ip) && Objects.equals(name, that.name));
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationUrl != null ? locationUrl : ip);
    }
}
