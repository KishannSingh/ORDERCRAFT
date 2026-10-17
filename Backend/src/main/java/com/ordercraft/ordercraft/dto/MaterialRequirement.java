package com.ordercraft.ordercraft.dto;

public class MaterialRequirement {

    private String materialName;
    private double quantity;
    private String unit;

    public MaterialRequirement() {
    }

    public MaterialRequirement(
            String materialName,
            double quantity,
            String unit) {

        this.materialName = materialName;
        this.quantity = quantity;
        this.unit = unit;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}