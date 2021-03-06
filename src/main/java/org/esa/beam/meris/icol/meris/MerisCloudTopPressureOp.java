package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.cloud.CloudTopPressureOp;

import java.awt.Rectangle;

/**
 * Operator for cloud top pressure computation for AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Icol.CloudTopPressure",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Operator for cloud top pressure computation for AE correction..")
public class MerisCloudTopPressureOp extends MerisBasisOp {

    private static final String INVALID_EXPRESSION = "l1_flags.INVALID";

    @SourceProduct(alias = "input")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private boolean useUserCtp;
    @Parameter
    private double userCtp;

    private Band invalidBand;

    @Override
    public void initialize() throws OperatorException {
        if (useUserCtp) {
            targetProduct = createCompatibleProduct(sourceProduct, "MER_CTP", "MER_L2");
            targetProduct.addBand("cloud_top_press", ProductData.TYPE_FLOAT32);

            BandMathsOp baOp = BandMathsOp.createBooleanExpressionBand(INVALID_EXPRESSION, sourceProduct);
            invalidBand = baOp.getTargetProduct().getBandAt(0);
        } else {
            targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudTopPressureOp.class), GPF.NO_PARAMS,
                                              sourceProduct);
        }
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 1);
        try {
            Tile isInvalid = getSourceTile(invalidBand, rect);
            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    if (isInvalid.getSampleBoolean(x, y)) {
                        targetTile.setSample(x, y, 0);
                    } else {
                        targetTile.setSample(x, y, userCtp);
                    }
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MerisCloudTopPressureOp.class);
        }
    }
}
