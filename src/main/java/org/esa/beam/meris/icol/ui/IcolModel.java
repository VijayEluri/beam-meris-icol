package org.esa.beam.meris.icol.ui;

import com.bc.ceres.binding.PropertyContainer;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;

import java.util.HashMap;
import java.util.Map;

public class IcolModel {
    // MerisReflectanceCorrectionOp
    @Parameter(defaultValue = "true")
    private boolean exportRhoToaRayleigh = true;
    @Parameter(defaultValue = "true")
    private boolean exportRhoToaAerosol = true;
    @Parameter(defaultValue = "true")
    private boolean exportAeRayleigh = true;
    @Parameter(defaultValue = "true")
    private boolean exportAeAerosol = true;
    @Parameter(defaultValue = "true")
    private boolean exportAlphaAot = true;

    // Cloud
    @Parameter
    private String cloudMaskExpression;
    
    // CTP
    @Parameter(defaultValue = "false")
    private boolean useUserCtp = false;
    @Parameter(interval = "[0.0, 1013.0]", defaultValue = "1013.0")
    private double userCtp = 1013.0;

    // MerisAeAerosolOp
    @Parameter(defaultValue = "true")
    boolean icolAerosolForWater = true;
    @Parameter(defaultValue = "false")
    boolean icolAerosolCase2 = false;
    @Parameter(interval = "[440.0, 2225.0]", defaultValue = "550.0")
    private double userAerosolReferenceWavelength = 550.0;
    @Parameter(interval = "[-2.1, -0.4]", defaultValue = "-1")
    private double userAlpha = -1.0;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0.2")
    private double userAot = 0.2;

    // General
//    @Parameter(defaultValue = "true")
    private boolean reshapedConvolution = true;
    @Parameter(defaultValue = "false")
    private boolean openclConvolution = false;
    @Parameter(defaultValue = "64")
    private int tileSize = 64;
    @Parameter(defaultValue = "EVERYWHERE", valueSet = {"EVERYWHERE", "COASTAL_ZONE", "COASTAL_OCEAN", "OCEAN"})
    private AeArea aeArea;
    @Parameter(defaultValue = "true")
    boolean useAdvancedLandWaterMask = true;

    // Landsat
    @Parameter(defaultValue = "0", valueSet = {"0", "1"})
    private int landsatTargetResolution = 1; // 1200m
    @Parameter(defaultValue = "0", valueSet = {"0", "1", "2", "3"})
    private int landsatOutputProductType = LandsatConstants.OUTPUT_PRODUCT_TYPE_DOWNSCALE; // standard product
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.32")
    private double landsatUserOzoneContent = LandsatConstants.DEFAULT_OZONE_CONTENT;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.0")
    private double landsatUserPSurf = LandsatConstants.DEFAULT_SURFACE_PRESSURE;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "288.0")
    private double landsatUserTm60 = LandsatConstants.DEFAULT_SURFACE_TM_APPARENT_TEMPERATURE;

    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyBrightnessFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdsiFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.3")
    private double cloudBrightnessThreshold = LandsatConstants.DEFAULT_BRIGHTNESS_THRESHOLD;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2")
    private double cloudNdviThreshold = LandsatConstants.DEFAULT_NDVI_CLOUD_THRESHOLD;
    @Parameter(interval = "[0.0, 10.0]", defaultValue = "3.0")
    private double cloudNdsiThreshold = LandsatConstants.DEFAULT_NDSI_THRESHOLD;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0")
    private double cloudTM6Threshold = LandsatConstants.DEFAULT_TM6_CLOUD_THRESHOLD;

    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2")
    private double landNdviThreshold = LandsatConstants.DEFAULT_NDVI_LAND_THRESHOLD;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "1200.0")
    private double landTM6Threshold = LandsatConstants.DEFAULT_TM6_LAND_THRESHOLD;
    @Parameter(defaultValue = LandsatConstants.LAND_FLAGS_SUMMER, valueSet = {LandsatConstants.LAND_FLAGS_SUMMER,
            LandsatConstants.LAND_FLAGS_WINTER})
    private String landsatSeason = LandsatConstants.LAND_FLAGS_SUMMER;
    @Parameter
    private String landsatOutputProductsDir = System.getProperty("user.home");


    @Parameter(defaultValue = "0", valueSet = {"0", "1"})
    private int productType = 0;
    private Product sourceProduct;
    private Product cloudMaskProduct;
    private PropertyContainer propertyContainer;


    public IcolModel() {
        propertyContainer = PropertyContainer.createObjectBacked(this, new ParameterDescriptorFactory());
        aeArea = AeArea.EVERYWHERE;
    }

    public Product getSourceProduct() {
        return sourceProduct;
    }

    public Product getCloudMaskProduct() {
        return cloudMaskProduct;
    }

    private int getLandsatTargetResolution() {
        int landsatTargetResolutionValue = -1;
        if (landsatTargetResolution == 0) {
            landsatTargetResolutionValue = 300;
        } else if (landsatTargetResolution == 1) {
            landsatTargetResolutionValue = 1200;
        }
        return landsatTargetResolutionValue;
    }

    public int getLandsatOutputProductType() {
        return landsatOutputProductType;
    }

    public PropertyContainer getPropertyContainer() {
        return propertyContainer;
    }

    public Map<String, Object> getMerisParameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configMerisCtp(params);
        params.put("cloudMaskExpression", cloudMaskExpression);
        params.put("productType", productType);
        configAeAerosolOp(params);
        configMerisProcessing(params);
        configMerisReverseRhoToaOp(params);
        configGeneral(params);
        return params;
    }

    public Map<String, Object> getLandsatParameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configAeAerosolOp(params);
        configGeneral(params);
        configLandsatOp(params);
        return params;
    }

    private void configMerisReverseRhoToaOp(HashMap<String, Object> params) {
        params.put("exportRhoToaRayleigh", exportRhoToaRayleigh);
        params.put("exportRhoToaAerosol", exportRhoToaAerosol);
        params.put("exportAeRayleigh", exportAeRayleigh);
        params.put("exportAeAerosol", exportAeAerosol);
        params.put("exportAlphaAot", exportAlphaAot);
    }

    private void configMerisCtp(HashMap<String, Object> params) {
        params.put("useUserCtp", useUserCtp);
        params.put("userCtp", userCtp);
    }

    private void configMerisProcessing(HashMap<String, Object> params) {
        params.put("icolAerosolForWater", icolAerosolForWater);
        params.put("icolAerosolCase2", icolAerosolCase2);
        params.put("useAdvancedLandWaterMask", useAdvancedLandWaterMask);
    }

    private void configAeAerosolOp(HashMap<String, Object> params) {
        params.put("userAerosolReferenceWavelength", userAerosolReferenceWavelength);
        params.put("userAlpha", userAlpha);
        params.put("userAot", userAot);
    }

    private void configLandsatOp(HashMap<String, Object> params) {
        params.put("landsatTargetResolution", getLandsatTargetResolution());
        params.put("landsatOutputProductType", getLandsatOutputProductType());
        params.put("landsatUserOzoneContent", landsatUserOzoneContent);
        params.put("landsatUserPSurf", landsatUserPSurf);
        params.put("landsatUserTm60", landsatUserTm60);
        params.put("landsatCloudFlagApplyBrightnessFilter", landsatCloudFlagApplyBrightnessFilter);
        params.put("landsatCloudFlagApplyNdviFilter", landsatCloudFlagApplyNdviFilter);
        params.put("landsatCloudFlagApplyNdsiFilter", landsatCloudFlagApplyNdsiFilter);
        params.put("landsatCloudFlagApplyTemperatureFilter", landsatCloudFlagApplyTemperatureFilter);
        params.put("cloudBrightnessThreshold", cloudBrightnessThreshold);
        params.put("cloudNdviThreshold", cloudNdviThreshold);
        params.put("cloudNdsiThreshold", cloudNdsiThreshold);
        params.put("cloudTM6Threshold", cloudTM6Threshold);
        params.put("landsatLandFlagApplyNdviFilter", landsatLandFlagApplyNdviFilter);
        params.put("landsatLandFlagApplyTemperatureFilter", landsatLandFlagApplyTemperatureFilter);
        params.put("landNdviThreshold", landNdviThreshold);
        params.put("landTM6Threshold", landTM6Threshold);
        params.put("landsatSeason", landsatSeason);
        params.put("landsatOutputProductsDir", landsatOutputProductsDir);
    }

    private void configGeneral(HashMap<String, Object> params) {
//        params.put("tileSize", tileSize);    // currently no user option
//        params.put("openclConvolution", openclConvolution);     // currently no user option
        params.put("aeArea", aeArea);
    }
}
