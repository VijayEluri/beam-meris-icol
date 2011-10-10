/*
 * $Id: LandClassificationOp.java,v 1.2 2007/05/08 08:03:52 marcoz Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;


@OperatorMetadata(alias = "Meris.Icol.LandClassification",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "MERIS L2 land/water reclassification.")
public class MerisLandClassificationOp extends MerisBasisOp implements Constants {

    public static final String LAND_FLAGS = "land_classif_flags";

    public static final int F_MEGLINT = 0;
    public static final int F_LOINLD = 1;
    public static final int F_ISLAND = 2;
    public static final int F_LANDCONS = 3;
    public static final int F_ICE = 4;

    private L2AuxData auxData;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "rhotoa", optional = true)
    private Product rhoToaProduct;
    @SourceProduct(alias = "gascor")
    private Product gasCorProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "true",
            description = "If set to 'true', use new, improved land/water mask.")
    private boolean useAdvancedLandWaterMask;

//    private WatermaskClassifier classifier;
    private static int WATERMASK_RESOLUTION_DEFAULT = 50;

    @Override
    public void initialize() throws OperatorException {
//        if (useAdvancedLandWaterMask) {
//            try {
//                classifier = new WatermaskClassifier(WATERMASK_RESOLUTION_DEFAULT);
//            } catch (IOException e) {
//                getLogger().warning("Watermask classifier could not be initialized - fallback mode is used.");
//            }
//        }

        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }
        createTargetProduct();
    }

    private void createTargetProduct() {
        targetProduct = createCompatibleProduct(l1bProduct, "MER", "MER_L2");

        Band band = targetProduct.addBand(LAND_FLAGS, ProductData.TYPE_INT8);
        FlagCoding flagCoding = createFlagCoding();
        band.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

    public static FlagCoding createFlagCoding() {
        FlagCoding flagCoding = new FlagCoding(LAND_FLAGS);
        flagCoding.addFlag("F_MEGLINT", BitSetter.setFlag(0, F_MEGLINT), null);
        flagCoding.addFlag("F_LOINLD", BitSetter.setFlag(0, F_LOINLD), null);
        flagCoding.addFlag("F_ISLAND", BitSetter.setFlag(0, F_ISLAND), null);
        flagCoding.addFlag("F_LANDCONS", BitSetter.setFlag(0, F_LANDCONS), null);
        flagCoding.addFlag("F_ICE", BitSetter.setFlag(0, F_ICE), null);
        return flagCoding;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();
        try {
            Tile latTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_LAT_DS_NAME), rectangle);
            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle);
            Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), rectangle);
            Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), rectangle);
            Tile vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), rectangle);
            Tile windu = getSourceTile(l1bProduct.getTiePointGrid("zonal_wind"), rectangle);
            Tile windv = getSourceTile(l1bProduct.getTiePointGrid("merid_wind"), rectangle);
            Tile l1Flags = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME), rectangle);
            // TODO (mp 20.12.2010) - rho1, rho13, and rho14 are never used
//			Tile rho1 = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_1_BAND_NAME), rectangle);
//          Tile rho13 = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_13_BAND_NAME), rectangle);
//			Tile rho14 = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_14_BAND_NAME), rectangle);

//            Tile[] rhoToa = null;
//            if (rhoToaProduct != null) {
//                rhoToa = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
//                for (int i1 = 0; i1 < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i1++) {
//                    rhoToa[i1] = getSourceTile(rhoToaProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i1 + 1)), rectangle, pm);
//                }
//            }

            Tile rhoToa12 = null;
            Tile rhoToa13 = null;
            if (rhoToaProduct != null) {
                // see above code, only tiles of index 12 and 13 are used, so only they are loaded
                rhoToa12 = getSourceTile(rhoToaProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (12 + 1)), rectangle);
                rhoToa13 = getSourceTile(rhoToaProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (13 + 1)), rectangle);
            }

            Tile[] rhoNg = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < rhoNg.length; i++) {
                rhoNg[i] = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + (i + 1)), rectangle);
            }
            final Tile rhoNg_bb865_Tile = rhoNg[bb865];

            // pre-initialize constant values from auxdata
            final int b_thresh_0 = auxData.lap_b_thresh[0];
            final int b_thresh_1 = auxData.lap_b_thresh[1];
            final double a_thresh_0 = auxData.alpha_thresh[0];
            final double a_thresh_1 = auxData.alpha_thresh[1];
            final double[] r7thresh_tab_0 = auxData.r7thresh.getTab(0);
            final double[] r7thresh_tab_1 = auxData.r7thresh.getTab(1);
            final double[] r7thresh_tab_2 = auxData.r7thresh.getTab(2);
            final Object r7threshArray = auxData.r7thresh.getJavaArray();
            final Object r13threshArray = auxData.r13thresh.getJavaArray();

            GeoPos geoPos = null;
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y += Constants.SUBWIN_HEIGHT) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x += Constants.SUBWIN_WIDTH) {

                    /* v7: compute Glint reflectance here (only if there are water/land pixels) */
                    /* first wind modulus at window corner */
                    double windm = windu.getSampleFloat(x, y) * windu.getSampleFloat(x, y);
                    windm += windv.getSampleFloat(x, y) * windv.getSampleFloat(x, y);
                    windm = Math.sqrt(windm);
                    /* then wind azimuth */
                    double phiw = azimuth(windu.getSampleFloat(x, y), windv.getSampleFloat(x, y));
                    /* and "scattering" angle */
                    double chiw = MathUtils.RTOD * (Math.acos(Math.cos(saa.getSampleFloat(x, y) - phiw)));
                    double deltaAzimuth = HelperFunctions.computeAzimuthDifference(vaa.getSampleFloat(x, y), saa.getSampleFloat(x, y));
                    /* allows to retrieve Glint reflectance for wurrent geometry and wind */
                    double rhoGlint = glintRef(sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), deltaAzimuth, windm, chiw);

                    FractIndex[] r7thresh_Index = FractIndex.createArray(3);  /* v4.4 */
                    /* set up threshold for land-water discrimination */
                    Interp.interpCoord(sza.getSampleFloat(x, y), r7thresh_tab_0, r7thresh_Index[0]);
                    Interp.interpCoord(vza.getSampleFloat(x, y), r7thresh_tab_1, r7thresh_Index[1]);
                    /* take azimuth difference into account - v4.4 */
                    Interp.interpCoord(deltaAzimuth, r7thresh_tab_2, r7thresh_Index[2]);
                    /* DPM #2.6.26-1a */
                    final double r7thresh_val = Interp.interpolate(r7threshArray, r7thresh_Index);
                    final double r13thresh_val = Interp.interpolate(r13threshArray, r7thresh_Index);

                    /* process each pixel */
                    final int xWinEnd = Math.min(rectangle.x + rectangle.width, x + Constants.SUBWIN_WIDTH) - 1;
                    final int yWinEnd = Math.min(rectangle.y + rectangle.height, y + Constants.SUBWIN_HEIGHT) - 1;

                    if (x == 786 && y == 293) {
                        System.out.println("ice ix = " + x);
                    }
                    for (int iy = y; iy <= yWinEnd; iy++) {
                        for (int ix = x; ix <= xWinEnd; ix++) {
                            /* Land /Water re-classification - v4.2, updated for v7 */
                            /* DPM step 2.6.26 */

                            boolean is_water;
                            boolean is_land;
                            int b_thresh;           /*added V7 to manage 2 bands reclassif threshold LUT */
                            double a_thresh;  /*added V7 to manage 2 bands reclassif threshold LUT */
                            double rThresh;

                            /* test if pixel is water */
                            b_thresh = b_thresh_0;
                            a_thresh = a_thresh_0;
                            is_water = inland_waters(r7thresh_val, rhoNg, ix, iy, b_thresh, a_thresh);
                            /* the is_water flag is available in the output product as F_LOINLD */
                            targetTile.setSample(ix, iy, F_LOINLD, is_water);

                            /* test if pixel is land */
                            final float thresh_medg = 0.2f;
                            boolean is_glint = (rhoGlint >= thresh_medg * rhoNg_bb865_Tile.getSampleFloat(ix, iy));
                            if (is_glint) {
                                targetTile.setSample(ix, iy, F_MEGLINT, true);
                                b_thresh = b_thresh_0;
                                a_thresh = a_thresh_0;
                                rThresh = r7thresh_val;
                            } else {
                                b_thresh = b_thresh_1;
                                a_thresh = a_thresh_1;
                                rThresh = r13thresh_val;
                            }

                            boolean is_ice = false;
                            if (rhoToaProduct != null) {
                                /* test if pixel is ice (mdsi criterion, RS 2010/04/01) */
//                                final double mdsi = (rhoToa[12].getSampleDouble(x,y) - rhoToa[13].getSampleDouble(x,y))/
//                                                               (rhoToa[12].getSampleDouble(x,y) + rhoToa[13].getSampleDouble(x,y));
                                final double mdsi = (rhoToa12.getSampleDouble(x, y) - rhoToa13.getSampleDouble(x, y)) /
                                        (rhoToa12.getSampleDouble(x, y) + rhoToa13.getSampleDouble(x, y));
                                final float latitude = latTile.getSampleFloat(ix, iy);
                                is_ice = (Math.abs(latitude) > 60.0 && mdsi > 0.01 && l1Flags.getSampleBit(ix, iy, L1_F_BRIGHT));
                            }
                            targetTile.setSample(ix, iy, F_ICE, is_ice);

                            is_land = island(rThresh, rhoNg, ix, iy, b_thresh, a_thresh) || is_ice;

                            // DPM step 2.6.26-7
                            // DPM #2.6.26-6
                            // TODO: reconsider to user the is_land flag in decision; define logic in ambiguous cases!
                            // the water test is less severe than the land test
                            boolean is_land_consolidated = !is_water;
                            // the land test is more severe than the water test
                            if (is_glint && !l1Flags.getSampleBit(ix, iy, L1_F_LAND)) {
                                is_land_consolidated = is_land;
                            }

//                            if (useAdvancedLandWaterMask) {
//                                WatermaskStrategy strategy = new MerisWatermaskStrategy();
//                                final GeoCoding geoCoding = l1bProduct.getGeoCoding();
//                                if (geoCoding.canGetGeoPos()) {
//                                    geoPos = geoCoding.getGeoPos(new PixelPos(ix, iy), geoPos);
//                                    byte waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
//                                    if (!(waterMaskSample == WatermaskClassifier.INVALID_VALUE)) {
//                                        is_land = (waterMaskSample == WatermaskClassifier.LAND_VALUE);
//                                        is_land_consolidated = (waterMaskSample == WatermaskClassifier.LAND_VALUE);
//                                        final boolean isGlint = (is_glint && !is_land);
//                                        final boolean isLoinld = (is_water && !is_land);
//                                        targetTile.setSample(ix, iy, F_MEGLINT, isGlint);
//                                        targetTile.setSample(ix, iy, F_LOINLD, isLoinld);
//                                    }
//                                }
//                            }

                            /* the is_land flag is available in the output product as F_ISLAND */
                            targetTile.setSample(ix, iy, F_ISLAND, is_land);
                            /* the is_land_consolidated flag is available in the output product as F_LANDCONS */
                            targetTile.setSample(ix, iy, F_LANDCONS, is_land_consolidated);

                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Function glint_ref: interpolate glint reflectance from look-up table
     * inputs:
     * output:
     * return value:
     * success code: 0 OK
     * Reference: DPM L2 section 7.3.1 step 2.6.5.1.1
     * called by:
     * confidence
     * calls:
     * InterpCoord
     * GenericInterp
     */
    private double glintRef(double thetas, double thetav, double delta, double windm, double chiw) {
        FractIndex[] rogIndex = FractIndex.createArray(5);

        Interp.interpCoord(chiw, auxData.rog.getTab(0), rogIndex[0]);
        Interp.interpCoord(thetav, auxData.rog.getTab(1), rogIndex[1]);
        Interp.interpCoord(delta, auxData.rog.getTab(2), rogIndex[2]);
        Interp.interpCoord(windm, auxData.rog.getTab(3), rogIndex[3]);
        Interp.interpCoord(thetas, auxData.rog.getTab(4), rogIndex[4]);
        double rhoGlint = Interp.interpolate(auxData.rog.getJavaArray(), rogIndex);
        return rhoGlint;
    }

    /**
     * Function azimuth: compute the azimuth (in local topocentric coordinates)
     * of a vector
     * inputs:
     * x: component of vector along X (Eastward parallel) axis
     * y: component of vector along Y (Northward meridian) axis
     * return value:
     * azimuth of vector in degrees
     * references:
     * mission convention document PO-IS-ESA-GS-0561, para 6.3.4
     * L2 DPM step 2.6.5.1.1
     */
    private double azimuth(double x, double y) {
        if (y > 0.0) {
            // DPM #2.6.5.1.1-1
            return (MathUtils.RTOD * Math.atan(x / y));
        } else if (y < 0.0) {
            // DPM #2.6.5.1.1-5
            return (180.0 + MathUtils.RTOD * Math.atan(x / y));
        } else {
            // DPM #2.6.5.1.1-6
            return (x >= 0.0 ? 90.0 : 270.0);
        }
    }

    /**
     * Detects inland water.
     * Reference: DPM L2 step 2.6.11. Uses<br>
     * {@link L2AuxData#lap_beta_l}
     *
     * @param r7thresh_val threshold at 665nm
     * @param x
     * @param y
     * @param b_thresh
     * @param a_thresh
     * @return inland water flag
     */
    private boolean inland_waters(double r7thresh_val, Tile[] rhoNg, int x, int y, int b_thresh, double a_thresh) {
        /* DPM #2.6.26-4 */
        return (rhoNg[b_thresh].getSampleFloat(x, y) <= a_thresh * r7thresh_val) &&
                (auxData.lap_beta_l * rhoNg[bb865].getSampleFloat(x, y) < rhoNg[bb665].getSampleFloat(x, y));
    }

    private boolean island(double r7thresh_val, Tile[] rhoNg, int x, int y, int b_thresh, double a_thresh) {
        return (rhoNg[b_thresh].getSampleFloat(x, y) > a_thresh * r7thresh_val) &&
                (auxData.lap_beta_w * rhoNg[bb865].getSampleFloat(x, y) > rhoNg[bb665].getSampleFloat(x, y));
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MerisLandClassificationOp.class);
        }
    }

//    private interface WatermaskStrategy {
//
//        byte getWatermaskSample(float lat, float lon);
//    }
//
//    private class MerisWatermaskStrategy implements WatermaskStrategy {
//
//        @Override
//        public byte getWatermaskSample(float lat, float lon) {
//            int waterMaskSample = WatermaskClassifier.INVALID_VALUE;
//            if (classifier != null) {
//                waterMaskSample = classifier.getWaterMaskSample(lat, lon);
//            }
//            return (byte) waterMaskSample;
//        }
//    }
}
