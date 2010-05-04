/*
 * $Id: FresnelCoefficientOp.java,v 1.1 2007/03/27 12:51:41 marcoz Exp $
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
package org.esa.beam.meris.icol;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;


@OperatorMetadata(alias = "Meris.IcolFresnelCoeff",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Fresnel Coefficient computation.")
public class FresnelCoefficientOp extends MerisBasisOp {

    public static final String RHO_TOA_BAND_PREFIX = "rho_toa";

    private FresnelReflectionCoefficient fresnelCoefficient;
    private Band isLandBand;
    
    @SourceProduct(alias="input")
    private Product sourceProduct;
    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="land")
    private Product landProduct;
    @TargetProduct
    private Product targetProduct;
	

    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");
        Band[] sourceBands = sourceProduct.getBands();
        for (Band srcBand : sourceBands) {
			Band targetBand = targetProduct.addBand(srcBand.getName(), ProductData.TYPE_FLOAT32);
//			ProductUtils.copySpectralAttributes(srcBand, targetBand);
             ProductUtils.copySpectralBandProperties(srcBand, targetBand);
            targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
            targetBand.setNoDataValue(srcBand.getNoDataValue());
		}
        targetProduct.addBand("cf", ProductData.TYPE_FLOAT32);
        
        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(LandClassificationOp.LAND_FLAGS + ".F_LANDCONS", landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        try {
            loadFresnelReflectionCoefficient();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }
    
    private void loadFresnelReflectionCoefficient() throws IOException {
        String auxdataSrcPath = "auxdata/icol";
        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;
        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", new NullProgressMonitor());

        File fresnelFile = new File(auxdataTargetDir, FresnelReflectionCoefficient.FRESNEL_COEFF);
        final Reader reader = new FileReader(fresnelFile);
        fresnelCoefficient = new FresnelReflectionCoefficient(reader);
    }
    
    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
			String bandName = band.getName();
        	final double noDataValue = band.getNoDataValue();

            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle, pm);
        	Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), rectangle, pm);
        	Tile isLand = getSourceTile(isLandBand, rectangle, pm);
        	if (bandName.equals("cf")) {
        		bandName = sourceProduct.getBands()[0].getName();
        	}
        	Tile srcTile = getSourceTile(sourceProduct.getBand(bandName), rectangle, pm);
        	
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
					float rhoNg = srcTile.getSampleFloat(x, y);
					if (band.getName().equals("cf")) {
						if (rhoNg != noDataValue && !isLand.getSampleBoolean(x, y)) {
							final double rs = fresnelCoefficient.getCoeffFor(sza.getSampleDouble(x, y));
							final double rv = fresnelCoefficient.getCoeffFor(vza.getSampleDouble(x, y));
							final double cf = 1 + rs + rv;
							targetTile.setSample(x, y, cf);
						}
					} else {
						if (rhoNg != noDataValue && !isLand.getSampleBoolean(x, y)) {
							final double rs = fresnelCoefficient.getCoeffFor(sza.getSampleDouble(x, y));
							final double rv = fresnelCoefficient.getCoeffFor(vza.getSampleDouble(x, y));
							final double cf = 1 + rs + rv;
							targetTile.setSample(x, y, rhoNg * cf);
						} else {
							targetTile.setSample(x, y, rhoNg);
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
            super(FresnelCoefficientOp.class);
        }
    }
}