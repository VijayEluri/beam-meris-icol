package org.esa.beam.meris.icol.tm;

/**
 * This interface is a container for constants specific for LANDSAT-products.
 *
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public interface TmConstants {

    String LANDSAT5_RADIANCE_1_BAND_NAME = "radiance_1_blue_30m";
    String LANDSAT5_RADIANCE_2_BAND_NAME = "radiance_2_green_30m";
    String LANDSAT5_RADIANCE_3_BAND_NAME = "radiance_3_red_30m";
    String LANDSAT5_RADIANCE_4_BAND_NAME = "radiance_4_nearir_30m";
    String LANDSAT5_RADIANCE_5_BAND_NAME = "radiance_5_midir_30m";
    String LANDSAT5_RADIANCE_6_BAND_NAME = "radiance_6_thermalir_120m";
    String LANDSAT5_RADIANCE_7_BAND_NAME = "radiance_7_midir_30m";

    int LANDSAT5_RADIANCE_1_BAND_INDEX = 0;
    int LANDSAT5_RADIANCE_2_BAND_INDEX = 1;
    int LANDSAT5_RADIANCE_3_BAND_INDEX = 2;
    int LANDSAT5_RADIANCE_4_BAND_INDEX = 3;
    int LANDSAT5_RADIANCE_5_BAND_INDEX = 4;
    int LANDSAT5_RADIANCE_6_BAND_INDEX = 5;
    int LANDSAT5_RADIANCE_7_BAND_INDEX = 6;

    int LANDSAT5_FR_ORIG = 30;
    int LANDSAT5_RR = 300;
    int LANDSAT5_FR = 1200;

    String LANDSAT5_GASEOUS_TRANSMITTANCE_BAND_NAME = "gas_transmittance";
    String LANDSAT5_RAYLEIGH_SCATTERING_BAND_NAME = "rayleigh_scatt";
    String LANDSAT5_FRESNEL_REFLECTION_BAND_NAME = "fresnel_reflec";
    String LANDSAT5_AEROSOL_SCATTERING_BAND_NAME = "aerosol_scatt";

    String LANDSAT5_CTP_BAND_NAME = "ctp";

    /**
     * The effective wavelengths (ICOL+ ATBD D4, table 1)
     */
    float[] LANDSAT5_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS = {
            478.37f,
            560.58f,
            660.75f,
            831.47f,
            1643.73f,
            11450.0f,
            2225.20f
    };

    /**
     * The names of the Landsat5 TM spectral band names.
     */
    String[] LANDSAT5_RADIANCE_BAND_NAMES = {
            LANDSAT5_RADIANCE_1_BAND_NAME, // 0
            LANDSAT5_RADIANCE_2_BAND_NAME, // 1
            LANDSAT5_RADIANCE_3_BAND_NAME, // 2
            LANDSAT5_RADIANCE_4_BAND_NAME, // 3
            LANDSAT5_RADIANCE_5_BAND_NAME, // 4
            LANDSAT5_RADIANCE_6_BAND_NAME, // 5
            LANDSAT5_RADIANCE_7_BAND_NAME, // 6
    };

    String LANDSAT5_RADIANCE_BAND_PREFIX = "radiance";
//    String LANDSAT5_REFLECTANCE_BAND_PREFIX = "reflectance";
    String LANDSAT5_REFLECTANCE_BAND_PREFIX = "rho_toa";

    String LANDSAT5_REFLECTANCE_1_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm1";
    String LANDSAT5_REFLECTANCE_2_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm2";
    String LANDSAT5_REFLECTANCE_3_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm3";
    String LANDSAT5_REFLECTANCE_4_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm4";
    String LANDSAT5_REFLECTANCE_5_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm5";
    String LANDSAT5_REFLECTANCE_6_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm6";
    String LANDSAT5_REFLECTANCE_7_BAND_NAME = LANDSAT5_REFLECTANCE_BAND_PREFIX + "_tm7";

    /**
     * The names of the Meris Level 1 spectral band names.
     */
    String[] LANDSAT5_REFLECTANCE_BAND_NAMES = {
            LANDSAT5_REFLECTANCE_1_BAND_NAME, // 0
            LANDSAT5_REFLECTANCE_2_BAND_NAME, // 1
            LANDSAT5_REFLECTANCE_3_BAND_NAME, // 2
            LANDSAT5_REFLECTANCE_4_BAND_NAME, // 3
            LANDSAT5_REFLECTANCE_5_BAND_NAME, // 4
            LANDSAT5_REFLECTANCE_6_BAND_NAME, // 5
            LANDSAT5_REFLECTANCE_7_BAND_NAME, // 6
    };

    int LANDSAT5_NUM_SPECTRAL_BANDS = LANDSAT5_RADIANCE_BAND_NAMES.length;

    String LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX = "gas_transmittance";

    String LANDSAT5_GAS_TRANSMITTANCE_1_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm1";
    String LANDSAT5_GAS_TRANSMITTANCE_2_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm2";
    String LANDSAT5_GAS_TRANSMITTANCE_3_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm3";
    String LANDSAT5_GAS_TRANSMITTANCE_4_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm4";
    String LANDSAT5_GAS_TRANSMITTANCE_5_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm5";
    String LANDSAT5_GAS_TRANSMITTANCE_6_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm6";
    String LANDSAT5_GAS_TRANSMITTANCE_7_BAND_NAME = LANDSAT5_GAS_TRANSMITTANCE_BAND_PREFIX  + "_tm7";

    /**
     * The names of the gaseous transmittance spectral band names.
     */
    String[] LANDSAT5_GAS_TRANSMITTANCE_BAND_NAMES = {
            LANDSAT5_GAS_TRANSMITTANCE_1_BAND_NAME, // 0
            LANDSAT5_GAS_TRANSMITTANCE_2_BAND_NAME, // 1
            LANDSAT5_GAS_TRANSMITTANCE_3_BAND_NAME, // 2
            LANDSAT5_GAS_TRANSMITTANCE_4_BAND_NAME, // 3
            LANDSAT5_GAS_TRANSMITTANCE_5_BAND_NAME, // 4
            LANDSAT5_GAS_TRANSMITTANCE_6_BAND_NAME, // 5
            LANDSAT5_GAS_TRANSMITTANCE_7_BAND_NAME, // 6
    };

    String LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX = "rayleigh_scatt";

    String LANDSAT5_RAYLEIGH_SCATT_1_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm1";
    String LANDSAT5_RAYLEIGH_SCATT_2_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm2";
    String LANDSAT5_RAYLEIGH_SCATT_3_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm3";
    String LANDSAT5_RAYLEIGH_SCATT_4_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm4";
    String LANDSAT5_RAYLEIGH_SCATT_5_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm5";
    String LANDSAT5_RAYLEIGH_SCATT_6_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm6";
    String LANDSAT5_RAYLEIGH_SCATT_7_BAND_NAME = LANDSAT5_RAYLEIGH_SCATT_BAND_PREFIX  + "_tm7";

    /**
     * The names of the Rayleigh scattering spectral band names.
     */
    String[] LANDSAT5_RAYLEIGH_SCATT_BAND_NAMES = {
            LANDSAT5_RAYLEIGH_SCATT_1_BAND_NAME, // 0
            LANDSAT5_RAYLEIGH_SCATT_2_BAND_NAME, // 1
            LANDSAT5_RAYLEIGH_SCATT_3_BAND_NAME, // 2
            LANDSAT5_RAYLEIGH_SCATT_4_BAND_NAME, // 3
            LANDSAT5_RAYLEIGH_SCATT_5_BAND_NAME, // 4
            LANDSAT5_RAYLEIGH_SCATT_6_BAND_NAME, // 5
            LANDSAT5_RAYLEIGH_SCATT_7_BAND_NAME, // 6
    };

    String LANDSAT5_AEROSOL_SCATT_BAND_PREFIX = "aerosol_scatt";

    String LANDSAT5_AEROSOL_SCATT_1_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm1";
    String LANDSAT5_AEROSOL_SCATT_2_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm2";
    String LANDSAT5_AEROSOL_SCATT_3_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm3";
    String LANDSAT5_AEROSOL_SCATT_4_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm4";
    String LANDSAT5_AEROSOL_SCATT_5_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm5";
    String LANDSAT5_AEROSOL_SCATT_6_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm6";
    String LANDSAT5_AEROSOL_SCATT_7_BAND_NAME = LANDSAT5_AEROSOL_SCATT_BAND_PREFIX  + "_tm7";

    /**
     * The names of the aerosol scattering spectral band names.
     */
    String[] LANDSAT5_AEROSOL_SCATT_BAND_NAMES = {
            LANDSAT5_AEROSOL_SCATT_1_BAND_NAME, // 0
            LANDSAT5_AEROSOL_SCATT_2_BAND_NAME, // 1
            LANDSAT5_AEROSOL_SCATT_3_BAND_NAME, // 2
            LANDSAT5_AEROSOL_SCATT_4_BAND_NAME, // 3
            LANDSAT5_AEROSOL_SCATT_5_BAND_NAME, // 4
            LANDSAT5_AEROSOL_SCATT_6_BAND_NAME, // 5
            LANDSAT5_AEROSOL_SCATT_7_BAND_NAME, // 6
    };

    float[] LANDSAT5_SOLAR_IRRADIANCES = {  // official USGC values, see also ICOL_D4, table 2
            1997.0f,
            1812.0f,
            1533.0f,
            1039.0f,
            230.8f,
            0.0f, // dummy
            84.9f
    };

    double SUN_EARTH_DISTANCE_SQUARE  = 2.240237947541881E22; // this is from current MERIS L2 Auxdata

    String LAND_FLAGS_SUMMER = "Summer";
    String LAND_FLAGS_WINTER = "Winter";

    double CTP_K_FACTOR = 15.0;  // in hPa/K . This is about 0.65K/100m. TBD!!

    double DEFAULT_OZONE_CONTENT = 0.32;
    double DEFAULT_SURFACE_PRESSURE = 1013.25;
    double DEFAULT_SURFACE_TM_APPARENT_TEMPERATURE = 300.0;
    double DEFAULT_BRIGHTNESS_THRESHOLD = 0.08;
    double DEFAULT_NDVI_CLOUD_THRESHOLD = 0.2;
    double DEFAULT_NDVI_LAND_THRESHOLD = 0.1;
    double DEFAULT_NDSI_THRESHOLD = 3.0;
    double DEFAULT_TM6_LAND_THRESHOLD = 300.0;
    double DEFAULT_TM6_CLOUD_THRESHOLD = 300.0;



    float[] LANDSAT5_NOMINAL_RAYLEIGH_OPTICAL_THICKNESS = {  // ICOL_D4, table 1
            0.17962f,
            0.09588f,
            0.04858f,
            0.01986f,
            0.00128f,
            0.0f, // dummy
            0.00038f
    };

    float[] LANDSAT5_O3_OPTICAL_THICKNESS = {  // ICOL_D4, table 1
            0.0065f,
            0.00319f,
            0.00183f,
            0.0003f,
            0.0f,
            0.0f, // dummy
            0.0f
    };

}
