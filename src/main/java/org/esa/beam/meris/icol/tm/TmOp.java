package org.esa.beam.meris.icol.tm;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.FresnelCoefficientOp;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.Instrument;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectRayleighOp;
import org.esa.beam.meris.icol.common.CloudDistanceOp;
import org.esa.beam.meris.icol.common.CoastDistanceOp;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.etm.EtmGaseousCorrectionOp;
import org.esa.beam.meris.icol.etm.EtmGaseousTransmittanceOp;
import org.esa.beam.meris.icol.meris.CloudLandMaskOp;
import org.esa.beam.meris.icol.utils.DebugUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "icol.ThematicMapper",
                  version = "1.1",
                  authors = "Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2007-2009 by Brockmann Consult",
                  description = "Performs a correction of the adjacency effect for LANDSAT TM L1b data.")
public class TmOp extends TmBasisOp {

    // todo: we need the ORIGINAL source product (mandatory) and the geometry product (optional)
    @SourceProduct(description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(interval = "[440.0, 2225.0]", defaultValue = "550.0", description = "The Aerosol optical thickness reference wavelength.")
    private double userAerosolReferenceWavelength;
    @Parameter(interval = "[-2.1, -0.4]", defaultValue = "-1", description = "The Angstrom coefficient.")
    private double userAlpha;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0.2", description = "The aerosol optical thickness at reference wavelength.")
    private double userAot;
    @Parameter(defaultValue = "false", description = "If set to 'true', case 2 waters are considered by AE correction algorithm.")
    private boolean icolAerosolCase2 = false;
    @Parameter(defaultValue = "true", description = "If set to 'true', the aerosol type over water is computed by AE correction algorithm.")
    private boolean icolAerosolForWater = true;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.25", description = "The surface pressure to be used by AE correction algorithm.")
    private double landsatUserPSurf;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0", description = "The TM band 6 temperature to be used by AE correction algorithm.")
    private double landsatUserTm60;
    @Parameter(interval = "[0.01, 1.0]", defaultValue = "0.32", description = "The ozone content to be used by AE correction algorithm.")
    private double landsatUserOzoneContent;
    @Parameter(defaultValue = "300", valueSet = {"300", "1200"}, description = "The AE correction grid resolution to be used by AE correction algorithm.")
    private int landsatTargetResolution;
    @Parameter(defaultValue = "0", valueSet = {"0", "1", "2", "3"}, description =
            "The output product: 0 = the source bands will only be downscaled to AE correction grid resolution; 1 = compute an AE corrected product; 2 = upscale an AE corrected product to original resolution; 3 = only the cloud and land flag bands will be computed; .")
    private int landsatOutputProductType;

    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyBrightnessFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdsiFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.3", description = "The cloud brightness threshold.")
    private double cloudBrightnessThreshold;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2", description = "The cloud NDVI threshold.")
    private double cloudNdviThreshold;
    @Parameter(interval = "[0.0, 10.0]", defaultValue = "3.0", description = "The cloud NDSI threshold.")
    private double cloudNdsiThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0", description = "The cloud TM band 6 temperature threshold.")
    private double cloudTM6Threshold;

    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2", description = "The land NDVIthreshold.")
    private double landNdviThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0", description = "The land TM band 6 temperature threshold.")
    private double landTM6Threshold;
    @Parameter(defaultValue = TmConstants.LAND_FLAGS_SUMMER, valueSet = {TmConstants.LAND_FLAGS_SUMMER,
            TmConstants.LAND_FLAGS_WINTER}, description = "The summer/winter option for TM band 6 temperature test.")
    private String landsatSeason = TmConstants.LAND_FLAGS_SUMMER;

    // general
    private static final int productType = 0;
    private boolean reshapedConvolution = true;     // currently no user option

    @Parameter(defaultValue = "false", description = "If set to 'true', the convolution shall be computed on GPU device if available.")
    private boolean openclConvolution = false;      // currently not used in TM
    @Parameter(defaultValue = "64")
    private int tileSize = 64;
    @Parameter(defaultValue = "COASTAL_OCEAN", valueSet = {"COASTAL_OCEAN", "OCEAN", "COASTAL_ZONE", "EVERYWHERE"},
               description = "The area where the AE correction will be applied.")
    private AeArea aeArea = AeArea.COASTAL_OCEAN;
    @Parameter(defaultValue = "false", description = "If set to 'true', the aerosol and fresnel correction term are exported as bands.")
    private boolean exportSeparateDebugBands = false;

    // LandsatReflectanceConversionOp
    @Parameter(defaultValue = "true")
    private boolean exportRhoToa = true;
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


    private String landsatStartTime;
    private String landsatStopTime;

    @Override
    public void initialize() throws OperatorException {
//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // todo: only for debugging purpose!!
        setStartStopTime();

        if (landsatOutputProductType == TmConstants.OUTPUT_PRODUCT_TYPE_DOWNSCALE) {
            targetProduct = createDownscaledProduct();
            ProductUtils.copyMetadata(sourceProduct, targetProduct);
            return;
        }

        Product downscaledSourceProduct = null;
        if (landsatOutputProductType == TmConstants.OUTPUT_PRODUCT_TYPE_AECORR) {
            // compute downscaled product (ATBD D4, section 5.3.1) if not already done...
            downscaledSourceProduct = sourceProduct;
        }

        if (landsatOutputProductType == TmConstants.OUTPUT_PRODUCT_TYPE_UPSCALE) {
            // check if both original and AE corrected product exists on AE grid, in parent directory...
//            final File sourceProductFileLocation = sourceProduct.getFileLocation();
            final String intermediateProductDir = System.getProperty("user.home");  // todo: make this a parameter
            final File downscaledSourceProductFile = new File(intermediateProductDir + File.separator +
                                                                      "L1N_" + sourceProduct.getName() + ".dim");
            final File aeCorrProductFile = new File(intermediateProductDir + File.separator +
                                                            "L1N_L1N_" + sourceProduct.getName() + ".dim");
            Product aeCorrProduct;
            try {
                downscaledSourceProduct = ProductIO.readProduct(downscaledSourceProductFile.getAbsolutePath());
            } catch (IOException e) {
                throw new OperatorException("Cannot read downscaled source product for AE correction: " + e.getMessage());
            }
            try {
                aeCorrProduct = ProductIO.readProduct(aeCorrProductFile.getAbsolutePath());
            } catch (IOException e) {
                throw new OperatorException("Cannot read AE corrected product for upscaling: " + e.getMessage());
            }
            Product upscaledProduct = createUpscaledToOriginalProduct(downscaledSourceProduct, aeCorrProduct);
            targetProduct = upscaledProduct;
            ProductUtils.copyMetadata(sourceProduct, targetProduct);
            return;
        }

        // compute conversion to reflectance and temperature (ATBD D4, section 5.3.2)
        Map<String, Product> radianceConversionInput = new HashMap<String, Product>(2);
        radianceConversionInput.put("l1g", sourceProduct);
        radianceConversionInput.put("geometry", downscaledSourceProduct);
        Map<String, Object> conversionParameters = new HashMap<String, Object>(2);
        conversionParameters.put("startTime", landsatStartTime);
        conversionParameters.put("stopTime", landsatStopTime);
        Product conversionProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmRadConversionOp.class), conversionParameters, radianceConversionInput);

        // compute gaseous transmittance (ATBD D4, section 5.3.3)
        Map<String, Product> gaseousTransmittanceInput = new HashMap<String, Product>(3);
        gaseousTransmittanceInput.put("l1g", sourceProduct);
        gaseousTransmittanceInput.put("geometry", downscaledSourceProduct);
        Map<String, Object> gaseousTransmittanceParameters = new HashMap<String, Object>(1);
        gaseousTransmittanceParameters.put("ozoneContent", landsatUserOzoneContent);
        Product gaseousTransmittanceProduct = null;
        if (sourceProduct.getProductType().startsWith("Landsat5")) {
            gaseousTransmittanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmGaseousTransmittanceOp.class), gaseousTransmittanceParameters, gaseousTransmittanceInput);
        } else if (sourceProduct.getProductType().startsWith("Landsat7")) {
            gaseousTransmittanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(EtmGaseousTransmittanceOp.class), gaseousTransmittanceParameters, gaseousTransmittanceInput);
        }

        // now we need to call the operator sequence as for MERIS, but adjusted to Landsat if needed...

        // Cloud mask
        // MERIS: Product cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudClassificationOp.class), ...
        // Landsat: provide new TmCloudClassificationOp (cloud mask product as described in ATBD section 5.4.1)
        Map<String, Product> cloudInput = new HashMap<String, Product>(2);
        cloudInput.put("refl", conversionProduct);
        Map<String, Object> cloudParameters = new HashMap<String, Object>(8);
        cloudParameters.put("landsatCloudFlagApplyBrightnessFilter", landsatCloudFlagApplyBrightnessFilter);
        cloudParameters.put("landsatCloudFlagApplyNdviFilter", landsatCloudFlagApplyNdviFilter);
        cloudParameters.put("landsatCloudFlagApplyNdsiFilter", landsatCloudFlagApplyNdsiFilter);
        cloudParameters.put("landsatCloudFlagApplyTemperatureFilter", landsatCloudFlagApplyTemperatureFilter);
        cloudParameters.put("cloudBrightnessThreshold", cloudBrightnessThreshold);
        cloudParameters.put("cloudNdviThreshold", cloudNdviThreshold);
        cloudParameters.put("cloudNdsiThreshold", cloudNdsiThreshold);
        cloudParameters.put("cloudTM6Threshold", cloudTM6Threshold);
        Product cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmCloudClassificationOp.class), cloudParameters, cloudInput);

        // Cloud Top Pressure
        // MERIS: Product ctpProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisCloudTopPressureOp.class),...
        // Landsat: provide new TmCloudTopPressureOp (CTP product as described in ATBD section 5.4.4)
        Map<String, Product> ctpInput = new HashMap<String, Product>(2);
        ctpInput.put("refl", conversionProduct);
        ctpInput.put("cloud", cloudProduct);
        Map<String, Object> ctpParameters = new HashMap<String, Object>(2);
        ctpParameters.put("userPSurf", landsatUserPSurf);
        ctpParameters.put("userTm60", landsatUserTm60);
        Product ctpProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmCloudTopPressureOp.class), ctpParameters, ctpInput);

        // gas product
        // MERIS: Product gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), ...
        // Landsat: provide adjusted TmGaseousCorrectionOp (gaseous transmittance as from ATBD section 5.3.3)
        Map<String, Product> gasInput = new HashMap<String, Product>(3);
        gasInput.put("refl", conversionProduct);
        gasInput.put("atmFunctions", gaseousTransmittanceProduct);
        Map<String, Object> gasParameters = new HashMap<String, Object>(2);
        gasParameters.put("exportTg", true);
        Product gasProduct = null;
        if (sourceProduct.getProductType().startsWith("Landsat5")) {
            gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmGaseousCorrectionOp.class), gasParameters, gasInput);
        } else if (sourceProduct.getProductType().startsWith("Landsat7")) {
            gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(EtmGaseousCorrectionOp.class), gasParameters, gasInput);
        }

        // Land classification:
        // MERIS: Product landProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), ...
        // Landsat: provide new TmLandClassificationOp (land mask product as described in ATBD section 5.4.1)
        Map<String, Product> landInput = new HashMap<String, Product>(2);
        landInput.put("refl", conversionProduct);
        Map<String, Object> landParameters = new HashMap<String, Object>(5);
        landParameters.put("landsatLandFlagApplyNdviFilter", landsatLandFlagApplyNdviFilter);
        landParameters.put("landsatLandFlagApplyTemperatureFilter", landsatLandFlagApplyTemperatureFilter);
        landParameters.put("landNdviThreshold", landNdviThreshold);
        landParameters.put("landTM6Threshold", landTM6Threshold);
        landParameters.put("landsatSeason", landsatSeason);
        Product landProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmLandClassificationOp.class), landParameters, landInput);

        Map<String, Product> cloudLandMaskInput = new HashMap<String, Product>(2);
        cloudLandMaskInput.put("cloud", cloudProduct);
        cloudLandMaskInput.put("land", landProduct);
        Map<String, Object> cloudLandMaskParameters = new HashMap<String, Object>(2);
        cloudLandMaskParameters.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        cloudLandMaskParameters.put("cloudExpression", "cloud_classif_flags.F_CLOUD");
        Product cloudLandMaskProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudLandMaskOp.class), cloudLandMaskParameters,
                                                         cloudLandMaskInput);

        FlagCoding landFlagCoding = TmLandClassificationOp.createFlagCoding();
        FlagCoding cloudFlagCoding = TmCloudClassificationOp.createFlagCoding();

        if (landsatOutputProductType == TmConstants.OUTPUT_PRODUCT_TYPE_FLAGS) {
            cloudProduct.getFlagCodingGroup().add(landFlagCoding);
            DebugUtils.addSingleDebugFlagBand(cloudProduct, landProduct, landFlagCoding, TmCloudClassificationOp.CLOUD_FLAGS);
            // if we only want to write the flag settings, we can stop here
            targetProduct = cloudProduct;
            return;
        }

        // Fresnel product:
        // MERIS: Product fresnelProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FresnelCoefficientOp.class), ...
        // Landsat: provide adjusted LandsatFresnelCoefficientOp
        Map<String, Product> fresnelInput = new HashMap<String, Product>(3);
        fresnelInput.put("l1b", conversionProduct);
        fresnelInput.put("land", landProduct);
        fresnelInput.put("input", gasProduct);
        Map<String, Object> fresnelParameters = new HashMap<String, Object>();
        Product fresnelProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FresnelCoefficientOp.class), fresnelParameters, fresnelInput);


        // Rayleigh correction:
        // MERIS: Product rayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(RayleighCorrectionOp.class),
        // Landsat: provide adjusted TmRayleighCorrectionOp
        Map<String, Product> rayleighInput = new HashMap<String, Product>(6);
        rayleighInput.put("refl", conversionProduct);
        rayleighInput.put("land", landProduct);
        rayleighInput.put("geometry", downscaledSourceProduct);
        rayleighInput.put("fresnel", fresnelProduct);
        rayleighInput.put("cloud", cloudProduct);
        rayleighInput.put("ctp", ctpProduct);
        Map<String, Object> rayleighParameters = new HashMap<String, Object>(3);
        rayleighParameters.put("correctWater", true);
        rayleighParameters.put("exportRayCoeffs", true);
        rayleighParameters.put("userPSurf", landsatUserPSurf);
        Product rayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmRayleighCorrectionOp.class), rayleighParameters, rayleighInput);

        // AE mask:
        // --> same operator as for MERIS
        Map<String, Product> aemaskRayleighInput = new HashMap<String, Product>(2);
        aemaskRayleighInput.put("source", conversionProduct);
        aemaskRayleighInput.put("land", landProduct);
        Map<String, Object> aemaskRayleighParameters = new HashMap<String, Object>(5);
        aemaskRayleighParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        aemaskRayleighParameters.put("aeArea", aeArea);
        aemaskRayleighParameters.put("reshapedConvolution", reshapedConvolution);
        aemaskRayleighParameters.put("correctionMode", IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        Product aemaskRayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectMaskOp.class), aemaskRayleighParameters, aemaskRayleighInput);

        Map<String, Product> aemaskAerosolInput = new HashMap<String, Product>(2);
        aemaskAerosolInput.put("source", conversionProduct);
        aemaskAerosolInput.put("land", landProduct);
        Map<String, Object> aemaskAerosolParameters = new HashMap<String, Object>(5);
        aemaskAerosolParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        aemaskAerosolParameters.put("aeArea", aeArea);
        aemaskAerosolParameters.put("reshapedConvolution", reshapedConvolution);
        aemaskAerosolParameters.put("correctionMode", IcolConstants.AE_CORRECTION_MODE_AEROSOL);
        Product aemaskAerosolProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectMaskOp.class), aemaskAerosolParameters, aemaskAerosolInput);

        // Coast distance:
        Map<String, Product> coastDistanceInput = new HashMap<String, Product>(2);
        coastDistanceInput.put("source", conversionProduct);
        coastDistanceInput.put("land", landProduct);
        Map<String, Object> distanceParameters = new HashMap<String, Object>(4);
        distanceParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        distanceParameters.put("waterExpression", "land_classif_flags.F_LOINLD");
        distanceParameters.put("correctOverLand", aeArea.correctOverLand());
        distanceParameters.put("numDistances", 2);
        Product coastDistanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CoastDistanceOp.class), distanceParameters, coastDistanceInput);


        // Cloud distance:
        Map<String, Product> cloudDistanceInput = new HashMap<String, Product>(2);
        cloudDistanceInput.put("source", conversionProduct);
        cloudDistanceInput.put("cloud", cloudProduct);
        Map<String, Object> cloudDistanceParameters = new HashMap<String, Object>(0);
        Product cloudDistanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudDistanceOp.class), cloudDistanceParameters, cloudDistanceInput);

        // zMax:
        // --> same operator as for MERIS, provide necessary TPGs
        Map<String, Product> zmaxInput = new HashMap<String, Product>(4);
        zmaxInput.put("source", conversionProduct);
        zmaxInput.put("distance", coastDistanceProduct);
        zmaxInput.put("ae_mask", aemaskRayleighProduct);
        Map<String, Object> zmaxParameters = new HashMap<String, Object>(2);
        String aeMaskExpression = AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH + ".aep";
        if (aeArea.correctOverLand()) {
            aeMaskExpression = "true";
        }
        zmaxParameters.put("aeMaskExpression", aeMaskExpression);
        zmaxParameters.put("distanceBandName", CoastDistanceOp.COAST_DISTANCE);
        Product zmaxProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), zmaxParameters, zmaxInput);


        // zMaxCloud:
        // --> same operator as for MERIS, provide necessary TPGs
        Map<String, Product> zmaxCloudInput = new HashMap<String, Product>(5);
        zmaxCloudInput.put("source", conversionProduct);
        zmaxCloudInput.put("ae_mask", aemaskRayleighProduct);
        zmaxCloudInput.put("distance", cloudDistanceProduct);
        Map<String, Object> zmaxCloudParameters = new HashMap<String, Object>();
        zmaxCloudParameters.put("aeMaskExpression", AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH + ".aep");
        zmaxCloudParameters.put("distanceBandName", CloudDistanceOp.CLOUD_DISTANCE);
        Product zmaxCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), zmaxCloudParameters, zmaxCloudInput);


        // AE Rayleigh:
        // --> same operator as for MERIS, distinguish Meris/Landsat case
        Map<String, Product> aeRayInput = new HashMap<String, Product>(8);
        aeRayInput.put("l1b", conversionProduct);
        aeRayInput.put("land", landProduct);
        aeRayInput.put("aemask", aemaskRayleighProduct);
        aeRayInput.put("ray1b", rayleighProduct);
        aeRayInput.put("rhoNg", gasProduct);
        aeRayInput.put("cloud", cloudProduct);
        aeRayInput.put("cloudLandMask", cloudLandMaskProduct);
        aeRayInput.put("zmax", zmaxProduct);
        aeRayInput.put("zmaxCloud", zmaxCloudProduct);
        Map<String, Object> aeRayParams = new HashMap<String, Object>(5);
        aeRayParams.put("landExpression", "land_classif_flags.F_LANDCONS");
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
            exportSeparateDebugBands = true;
        }
        aeRayParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeRayParams.put("reshapedConvolution", reshapedConvolution);
        if (sourceProduct.getProductType().startsWith("Landsat5")) {
            aeRayParams.put("instrument", Instrument.TM5);
        } else if (sourceProduct.getProductType().startsWith("Landsat7")) {
            aeRayParams.put("instrument", Instrument.ETM7);
        }
        Product aeRayProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectRayleighOp.class), aeRayParams, aeRayInput);

        // AE Aerosol:
        // --> same operator as for MERIS, distinguish Meris/Landsat case
        Map<String, Product> aeAerInput = new HashMap<String, Product>(9);
        aeAerInput.put("l1b", conversionProduct);
        aeAerInput.put("land", landProduct);
        aeAerInput.put("aemask", aemaskAerosolProduct);
        aeAerInput.put("zmax", zmaxProduct);
        aeAerInput.put("ae_ray", aeRayProduct);
        aeAerInput.put("cloud", cloudProduct);
        aeAerInput.put("ctp", ctpProduct);
        aeAerInput.put("zmaxCloud", zmaxCloudProduct);
        Map<String, Object> aeAerosolParams = new HashMap<String, Object>(9);
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS"))
            exportSeparateDebugBands = true;
        aeAerosolParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeAerosolParams.put("icolAerosolForWater", icolAerosolForWater);
        aeAerosolParams.put("userAerosolReferenceWavelength", userAerosolReferenceWavelength);
        aeAerosolParams.put("userAlpha", userAlpha);
        aeAerosolParams.put("userAot", userAot);
        aeAerosolParams.put("userPSurf", landsatUserPSurf);
        aeAerosolParams.put("reshapedConvolution", reshapedConvolution);
        aeAerosolParams.put("landExpression", "land_classif_flags.F_LANDCONS");
        aeAerosolParams.put("instrument", sourceProduct.getProductType());
        Product aeAerProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmAeAerosolOp.class), aeAerosolParams, aeAerInput);

        // AE Rayleigh/Aerosol merge:
        Map<String, Product> aeTotalInput = new HashMap<String, Product>(9);
        aeTotalInput.put("original", conversionProduct);
        aeTotalInput.put("aemaskAer", aemaskAerosolProduct);
        aeTotalInput.put("aemaskRay", aemaskRayleighProduct);
        aeTotalInput.put("aeRay", aeRayProduct);
        aeTotalInput.put("aeAer", aeAerProduct);
        Map<String, Object> aeTotalParams = new HashMap<String, Object>(9);
        aeTotalParams.put("productType", productType);
        Product aeTotalProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmAeMergeOp.class), aeTotalParams, aeTotalInput);

        // Reverse radiance:
        // MERIS: correctionProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisRadianceCorrectionOp.class), ...
        // Landsat: provide adjusted TmReflectanceCorrectionOp
        Product correctionProduct = null;
        // radiance output product
        Map<String, Product> radianceCorrectionInput = new HashMap<String, Product>(6);
        radianceCorrectionInput.put("refl", conversionProduct);
        radianceCorrectionInput.put("gascor", gasProduct);
        radianceCorrectionInput.put("ae_ray", aeRayProduct);
        radianceCorrectionInput.put("ae_aerosol", aeAerProduct);
        radianceCorrectionInput.put("aemaskRayleigh", aemaskRayleighProduct);
        radianceCorrectionInput.put("aemaskAerosol", aemaskAerosolProduct);
        Map<String, Object> radianceCorrectionParams = new HashMap<String, Object>(1);
        correctionProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmRadianceCorrectionOp.class), radianceCorrectionParams, radianceCorrectionInput);

        // output:
        correctionProduct.getFlagCodingGroup().add(cloudFlagCoding);
        DebugUtils.addSingleDebugFlagBand(correctionProduct, cloudProduct, cloudFlagCoding, TmCloudClassificationOp.CLOUD_FLAGS);
        correctionProduct.getFlagCodingGroup().add(landFlagCoding);
        DebugUtils.addSingleDebugFlagBand(correctionProduct, landProduct, landFlagCoding, TmLandClassificationOp.LAND_FLAGS);
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
            DebugUtils.addRad2ReflDebugBands(correctionProduct, conversionProduct);
            DebugUtils.addRayleighCorrDebugBands(correctionProduct, rayleighProduct);
//            DebugUtils.addSingleDebugBand(correctionProduct, ctpProduct, TmConstants.LANDSAT5_CTP_BAND_NAME);
//            DebugUtils.addSingleDebugBand(correctionProduct, aemaskRayleighProduct, AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH);
//            DebugUtils.addSingleDebugBand(correctionProduct, aemaskAerosolProduct, AdjacencyEffectMaskOp.AE_MASK_AEROSOL);
//            DebugUtils.addSingleDebugBand(correctionProduct, zmaxProduct, ZmaxOp.ZMAX + "_1");
//            DebugUtils.addSingleDebugBand(correctionProduct, zmaxProduct, ZmaxOp.ZMAX + "_2");
//            DebugUtils.addSingleDebugBand(correctionProduct, coastDistanceProduct, CoastDistanceOp.COAST_DISTANCE + "_1");
//            DebugUtils.addSingleDebugBand(correctionProduct, coastDistanceProduct, CoastDistanceOp.COAST_DISTANCE + "_2");
//            DebugUtils.addSingleDebugBand(correctionProduct, cloudDistanceProduct, CloudDistanceOp.CLOUD_DISTANCE);
            DebugUtils.addAeRayleighProductDebugBands(correctionProduct, aeRayProduct);
            DebugUtils.addAeAerosolProductDebugBands(correctionProduct, aeAerProduct);
//            DebugUtils.addAeTotalProductDebugBands(correctionProduct, aeTotalProduct);
        }

        // test:
        targetProduct = correctionProduct;
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        // upscale all bands to Tm full resolution
//        Product upscaledProduct = createUpscaledToOriginalProduct(downscaledSourceProduct, correctionProduct);
//        targetProduct = upscaledProduct;
    }

    private Product createDownscaledProduct() {
        Product geometryProduct;
        Map<String, Product> geometryInput = new HashMap<String, Product>(1);
        geometryInput.put("l1g", sourceProduct);
        Map<String, Object> geometryParameters = new HashMap<String, Object>(3);
        geometryParameters.put("landsatTargetResolution", landsatTargetResolution);
        geometryParameters.put("startTime", landsatStartTime);
        geometryParameters.put("stopTime", landsatStopTime);
        geometryProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmGeometryOp.class), geometryParameters, geometryInput);
        return geometryProduct;
    }

    private Product createUpscaledToOriginalProduct(Product geometryProduct, Product correctionProduct) {
        Map<String, Product> aeUpscaleInput = new HashMap<String, Product>(9);
        aeUpscaleInput.put("l1b", sourceProduct);
        aeUpscaleInput.put("geometry", geometryProduct);
        aeUpscaleInput.put("corrected", correctionProduct);
        Map<String, Object> aeUpscaleParams = new HashMap<String, Object>(9);

        return GPF.createProduct(OperatorSpi.getOperatorAlias(TmUpscaleToOriginalOp.class), aeUpscaleParams, aeUpscaleInput);
    }

    /**
     * creates a new product with the same size
     *
     * @param sourceProduct
     * @param name
     * @param type
     * @return targetProduct
     */
    public Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyBaseGeoInfo(sourceProduct, targetProduct);

        return targetProduct;
    }

    private void setStartStopTime() {
        try {
            // finally we need dd-MM-yyyy hh:mm:ss
            if (sourceProduct.getStartTime() != null && sourceProduct.getEndTime() != null) {
                // this is the case for geometry product
                landsatStartTime = sourceProduct.getStartTime().toString().substring(0, 20);
                landsatStopTime = sourceProduct.getEndTime().toString().substring(0, 20);
            } else {
                // this is the case for original input product (GeoTIFF)
                // here we get yyyy-mm-dd:
                String acquisitionDate = sourceProduct.getMetadataRoot()
                        .getElement("L1_METADATA_FILE").getElement("PRODUCT_METADATA").getAttribute("ACQUISITION_DATE").getData().getElemString();

                String centerTime = sourceProduct.getMetadataRoot()
                        .getElement("L1_METADATA_FILE").getElement("PRODUCT_METADATA").getAttribute("SCENE_CENTER_SCAN_TIME").getData().getElemString().substring(0, 8);

                String landsatCenterTime = LandsatUtils.convertDate(acquisitionDate) + " " + centerTime;
                landsatStartTime = landsatCenterTime;
                landsatStopTime = landsatCenterTime;
                sourceProduct.setStartTime(ProductData.UTC.parse(landsatCenterTime));
                sourceProduct.setEndTime(ProductData.UTC.parse(landsatCenterTime));
            }

        } catch (ParseException e) {
            throw new OperatorException("Start or stop time invalid or has wrong format - must be 'yyyymmdd hh:mm:ss'.");
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmOp.class);
        }
    }
}
