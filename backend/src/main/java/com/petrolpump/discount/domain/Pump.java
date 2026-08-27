package com.petrolpump.discount.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "pumps")
public class Pump {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private double lat;
    private double lng;
    private double radiusMeters = 50;
    @Column(nullable = false, unique = true)
    private String redeemToken;
    @Column(length = 400)
    private String address = "";
    @Column(length = 120)
    private String contactName = "";
    @Column(length = 20)
    private String contactPhone = "";
    @Column(length = 400)
    private String mapsUrl = "";

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public double getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(double radiusMeters) { this.radiusMeters = radiusMeters; }
    public String getRedeemToken() { return redeemToken; }
    public void setRedeemToken(String redeemToken) { this.redeemToken = redeemToken; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getMapsUrl() { return mapsUrl; }
    public void setMapsUrl(String mapsUrl) { this.mapsUrl = mapsUrl; }
}
