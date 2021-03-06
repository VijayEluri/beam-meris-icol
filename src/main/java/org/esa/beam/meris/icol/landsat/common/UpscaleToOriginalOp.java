package org.esa.beam.meris.icol.landsat.common;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.Instrument;
import org.esa.beam.meris.icol.landsat.tm.TmBasisOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ScaleDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import java.awt.image.RenderedImage;

/**
 * Operator for upscaling of Landsat product from downscaled to original resolution after AE correction
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat.UpscaleToOriginal",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Landsat upscale to original resolution.")
public class UpscaleToOriginalOp extends TmBasisOp {

    @SourceProduct(alias = "l1b")
    private Product sourceProduct;
    @SourceProduct(alias = "downscaled")
    private Product downscaledProduct;
    @SourceProduct(alias = "corrected")
    private Product correctedProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter
    private Instrument instrument;

    @Override
    public void initialize() throws OperatorException {

        int width = sourceProduct.getSceneRasterWidth();
        int height = sourceProduct.getSceneRasterHeight();

        final float xScale = (float) width / correctedProduct.getSceneRasterWidth();
        final float yScale = (float) height / correctedProduct.getSceneRasterHeight();

        targetProduct = createTargetProduct(sourceProduct, "upscale_" + correctedProduct.getName(),
                                            sourceProduct.getProductType(),
                                            width, height);

        for (int i = 0; i < sourceProduct.getNumBands(); i++) {
            Band sourceBand = sourceProduct.getBandAt(i);

            Band targetBand;
            int dataType = sourceBand.getDataType();
            final String srcBandName = sourceBand.getName();
            if (srcBandName.startsWith("radiance")) {
                if (!srcBandName.startsWith("radiance_6")) {
                    targetBand = targetProduct.addBand(srcBandName, ProductData.TYPE_FLOAT32);
                    Band correctedBand = correctedProduct.getBand(srcBandName);
                    Band downscaledBand = downscaledProduct.getBand(srcBandName);

                    RenderedImage downscaledImage = downscaledBand.getSourceImage();
                    RenderedImage correctedImage = correctedBand.getSourceImage();
                    RenderedOp diffImage = SubtractDescriptor.create(downscaledImage, correctedImage, null);

                    // here we upscale the difference image (i.e., the AE correction)
                    // note that xscale, yscale may be 1 (in fact no upscaling) if source is a downscaled product
                    RenderedOp upscaledDiffImage = ScaleDescriptor.create(diffImage,
                                                                          xScale,
                                                                          yScale,
                                                                          0.0f, 0.0f,
                                                                          Interpolation.getInstance(
                                                                                  Interpolation.INTERP_BILINEAR),
                                                                          null);

                    // here we subtract the AE correction on original resolution
                    RenderedOp finalAeCorrectedImage = SubtractDescriptor.create(sourceBand.getGeophysicalImage(), upscaledDiffImage, null);
                    targetBand.setSourceImage(finalAeCorrectedImage);
                } else {
                    targetBand = targetProduct.addBand(srcBandName, dataType);
                    targetBand.setSourceImage(sourceBand.getSourceImage());
                    ProductUtils.copyRasterDataNodeProperties(sourceBand, targetBand);
                }
                OperatorUtils.copyBandProperties(sourceBand, targetBand);
            }
        }
    }

    private static Product createTargetProduct(Product sourceProduct, String name, String type, int width, int height) {

        Product targetProduct = new Product(name, type, width, height);
        OperatorUtils.copyProductBase(sourceProduct, targetProduct);
        return targetProduct;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(UpscaleToOriginalOp.class);
        }
    }
}