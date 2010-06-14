/*
 * $Id: MerisAeRayleighOp.java,v 1.5 2007/05/10 17:01:06 marcoz Exp $
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

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.FresnelReflectionCoefficient;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.RhoBracketAlgo;
import org.esa.beam.meris.icol.RhoBracketJaiConvolve;
import org.esa.beam.meris.icol.RhoBracketKernellLoop;
import org.esa.beam.meris.icol.common.AeMaskOp;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Map;


/**
 * Operator for Rayleigh part of AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.AERayleigh",
        version = "1.0",
        internal = true,
        authors = "Marco Zuehlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Contribution of rayleigh to the adjacency effect.")
public class MerisAeRayleighOp extends MerisBasisOp {

    private FresnelReflectionCoefficient fresnelCoefficient;
    private static final int NUM_BANDS = EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS - 2;
    private static final double NO_DATA_VALUE = -1.0;

    private static final double HR = 8000; // Rayleigh scale height
    private CoeffW coeffW;
    RhoBracketAlgo rhoBracketAlgo;

    private Band[] aeRayBands;
    private Band[] rhoAeRcBands;
    private Band[] rhoAgBracketBands;        // additional output for RS

    private Band[] fresnelDebugBands;
    private Band[] rayleighdebugBands;

    private Band isLandBand;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "aemask")
    private Product aemaskProduct;
    @SourceProduct(alias = "ray1b")
    private Product ray1bProduct;
    @SourceProduct(alias = "ray1bconv", optional=true)
    private Product ray1bconvProduct;
    @SourceProduct(alias = "rhoNg")
    private Product gasCorProduct;
    @SourceProduct(alias = "zmax")
    private Product zmaxProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private String landExpression;
    @Parameter(defaultValue="true")
    private boolean openclConvolution = true;
    @Parameter(defaultValue="true")
    private boolean reshapedConvolution;
    @Parameter
    private boolean exportSeparateDebugBands = false;
    private long convolutionTime = 0L;
    private int convolutionCount = 0;
    private int[] bandsToSkip;

    @Override
    public void initialize() throws OperatorException {
        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        bandsToSkip = new int[]{10, 14};
        createTargetProduct();

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private void loadAuxData() throws IOException {
        String auxdataSrcPath = "auxdata/icol";
        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;
        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", new NullProgressMonitor());

        File fresnelFile = new File(auxdataTargetDir, FresnelReflectionCoefficient.FRESNEL_COEFF);
        final Reader reader = new FileReader(fresnelFile);
        fresnelCoefficient = new FresnelReflectionCoefficient(reader);

        coeffW = new CoeffW(auxdataTargetDir, reshapedConvolution, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
    }

    private void createTargetProduct() {
        String productType = l1bProduct.getProductType();
        if (reshapedConvolution) {
            rhoBracketAlgo = new RhoBracketJaiConvolve(ray1bProduct, productType, coeffW, "brr_", 1,
                                                       EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS,
                                                       bandsToSkip);
        } else {
            rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        }

        targetProduct = createCompatibleProduct(l1bProduct, "ae_ray_" + l1bProduct.getName(), "MER_AE_RAY");
        aeRayBands = addBandGroup("rho_aeRay");
        rhoAeRcBands = addBandGroup("rho_ray_aerc");
        rhoAgBracketBands = addBandGroup("rho_ag_bracket");

        if (exportSeparateDebugBands) {
            rayleighdebugBands = addBandGroup("rho_aeRay_rayleigh");
            fresnelDebugBands = addBandGroup("rho_aeRay_fresnel");
        }

        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

    private Band[] addBandGroup(String prefix) {
        return OperatorUtils.addBandGroup(l1bProduct, EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS, bandsToSkip,
                targetProduct, prefix, NO_DATA_VALUE, true);
    }

    private Tile[] getSourceTiles(final Product inProduct, String bandPrefix, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        final Tile[] bandData = new Tile[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            String bandIdentifier = bandPrefix + "_" + (i + 1);
            bandData[j] = getSourceTile(inProduct.getBand(bandIdentifier), rectangle, pm);
            j++;
        }
        return bandData;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws OperatorException {

        Rectangle sourceRect = rhoBracketAlgo.mapTargetRect(targetRect);
        pm.beginTask("Processing frame...", targetRect.height + 1);
        try {
            // sources
            Tile isLand = getSourceTile(isLandBand, sourceRect, pm);

            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, pm);
            Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, pm);
            Tile[] zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
            Tile zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect, pm);
            Tile aep = getSourceTile(aemaskProduct.getBand(AeMaskOp.AE_MASK_RAYLEIGH), targetRect, pm);
            Tile cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRect, pm);

            Tile[] rhoNg = getSourceTiles(gasCorProduct, GaseousCorrectionOp.RHO_NG_BAND_PREFIX, targetRect, pm);
            Tile[] transRup = getSourceTiles(ray1bProduct, "transRv", targetRect, pm); //up
            Tile[] transRdown = getSourceTiles(ray1bProduct, "transRs", targetRect, pm); //down
            Tile[] tauR = getSourceTiles(ray1bProduct, "tauR", targetRect, pm);
            Tile[] sphAlbR = getSourceTiles(ray1bProduct, "sphAlbR", targetRect, pm);

            Tile[] rhoAg = getSourceTiles(ray1bProduct, "brr", sourceRect, pm);
            Tile[] rhoAgConv = null;
            if (openclConvolution && ray1bconvProduct != null) {
                rhoAgConv = getSourceTiles(ray1bconvProduct, "brr_conv", sourceRect, pm);
            }
            final RhoBracketAlgo.Convolver convolver = rhoBracketAlgo.createConvolver(this, rhoAg, targetRect, pm);

            //targets
            Tile[] aeRayTiles = OperatorUtils.getTargetTiles(targetTiles, aeRayBands);
            Tile[] rhoAeRcTiles = OperatorUtils.getTargetTiles(targetTiles, rhoAeRcBands);
            Tile[] rhoAgBracket = null;
            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                rhoAgBracket = OperatorUtils.getTargetTiles(targetTiles, rhoAgBracketBands);
            }

            Tile[] rayleighDebug = null;
            Tile[] fresnelDebug = null;
            if (exportSeparateDebugBands) {
                rayleighDebug = OperatorUtils.getTargetTiles(targetTiles, rayleighdebugBands);
                fresnelDebug = OperatorUtils.getTargetTiles(targetTiles, fresnelDebugBands);
            }

//            final int numBands = rhoNg.length;
            final int numBands = rhoNg.length-1;
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    for (int b = 0; b < numBands; b++) {
                        if (exportSeparateDebugBands) {
                            fresnelDebug[b].setSample(x, y, -1);
                            rayleighDebug[b].setSample(x, y, -1);
                        }
                    }
                    boolean isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
                    if (aep.getSampleInt(x, y) == 1 && !isCloud && rhoAg[0].getSampleFloat(x, y) != -1) {
                        long t1 = System.currentTimeMillis();
                        double[] means = new double [numBands];
                        if (!openclConvolution) {
                            means = convolver.convolvePixel(x, y, 1);
                        }
                        long t2 = System.currentTimeMillis();
                        convolutionCount++;
                        this.convolutionTime += (t2-t1);

                        final double muV = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        for (int b = 0; b < numBands; b++) {
                            double tmpRhoRayBracket = 0.0;
                            if (openclConvolution && ray1bconvProduct != null) {
                                tmpRhoRayBracket = rhoAgConv[b].getSampleFloat(x, y);
                            } else {
                                tmpRhoRayBracket = means[b];
                            }

                            // rayleigh contribution without AE (tmpRhoRayBracket)
                            double aeRayRay = 0.0;

                            // over water, compute the rayleigh contribution to the AE
                            final float rhoAgValue = rhoAg[b].getSampleFloat(x, y);
                            final float transRupValue = transRup[b].getSampleFloat(x, y);
                            final float tauRValue = tauR[b].getSampleFloat(x, y);
                            final float transRdownValue = transRdown[b].getSampleFloat(x, y);
                            final float sphAlbValue = sphAlbR[b].getSampleFloat(x, y);
                            aeRayRay = (transRupValue - Math
                                .exp(-tauRValue / muV))
                                * (tmpRhoRayBracket - rhoAgValue) * (transRdownValue /
                                (1d - tmpRhoRayBracket * sphAlbValue));

                            //compute the additional molecular contribution from the LFM  - ICOL+ ATBD eq. (10)
                            double zmaxPart = ZmaxOp.computeZmaxPart(zmaxs, x, y, HR);
                            double zmaxCloudPart = ZmaxOp.computeZmaxPart(zmaxCloud, x, y, HR);

                            final double r1v = fresnelCoefficient.getCoeffFor(sza.getSampleFloat(x, y));
                            double aeRayFresnelLand = 0.0d;
                            if (zmaxPart != 0) {
                                aeRayFresnelLand = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxPart;
                                if (isLand.getSampleBoolean(x, y)) {
                                   // contribution must be subtracted over land - ICOL+ ATBD section 4.2 
                                   aeRayFresnelLand *= -1.0;
                                }
                            }
                            double aeRayFresnelCloud = 0.0d;
                            if (zmaxCloudPart != 0) {
                                aeRayFresnelCloud = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxCloudPart;
                            }

                            if (exportSeparateDebugBands) {
                                fresnelDebug[b].setSample(x, y, aeRayFresnelLand+aeRayFresnelCloud);
                                rayleighDebug[b].setSample(x, y, aeRayRay);
                            }

                            final double aeRay = aeRayRay - aeRayFresnelLand - aeRayFresnelCloud;

                            aeRayTiles[b].setSample(x, y, aeRay);
                            //correct the top of aerosol reflectance for the AE_RAY effect
                            rhoAeRcTiles[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y) - aeRay);
                            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                rhoAgBracket[b].setSample(x, y, tmpRhoRayBracket);
                            }
                        }
                    } else {
                        for (int b = 0; b < numBands; b++) {
                            rhoAeRcTiles[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y));
                            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                rhoAgBracket[b].setSample(x, y, -1f);
                            }
                        }
                    }
                }
                pm.worked(1);
            }

        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MerisAeRayleighOp.class);
        }
    }
}
