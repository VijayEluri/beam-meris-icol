package org.esa.beam.meris.icol.landsat.etm;

import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class EtmRayleighCorrection implements Constants {
    private LocalHelperVariables lh;

    private L2AuxData auxdata;

    /**
     * Constructs the module
     */
    public EtmRayleighCorrection(L2AuxData auxData) {
        auxdata = auxData;
        lh = new LocalHelperVariables();
    }

    /**
     * Computes Rayleigh reflectance for all bands for a given geometry and pressure.
     * <p/>
     * <b>Input:</b> all parameters without <code>refRayl</code>, {@link org.esa.beam.meris.l2auxdata.L2AuxData#Rayscatt_coeff_s}
     * <br> <b>Output:</b> parameter <code>refRayl</code><br> <b>DPM ref.:</b> DPM L2, section 7.3.3.3.2 <br> <b>MEGS
     * ref.:</b> <code>ray_cor.c</code>, function <code>ref_rayleigh</code><br>
     *
     * @param delta_azimuth Azimuth difference (deltaphi)
     * @param sun_zenith    Sun zenith angle (thetas)
     * @param view_zenith   View zenith angle (thetav)
     * @param mus           Cosine of Sun zenith angle
     * @param muv           Cosine of view zenith angle
     * @param airMass       Air mass (M)
     * @param phaseRayl     Rayleigh phase function Fourier components (PR(s))
     * @param tauRayl       Rayleigh optical thickness (tauR0)
     * @param refRayl       Rayleigh reflectance for all bands (rhoR_4x4)
     */
    public void ref_rayleigh(double delta_azimuth, double sun_zenith, double view_zenith,
                             double mus, double muv, double airMass,
                             double[] phaseRayl, double[] tauRayl, double[] refRayl) {

        FractIndex tsi = lh.ref_rayleigh_i[0];         /* interp coordinates for thetas in LUT scale */
        FractIndex tvi = lh.ref_rayleigh_i[1];          /* interp coordinates for thetav in LUT scale */

        double mud = Math.cos(RAD * delta_azimuth); /* used for all bands, compute once */
        double mu2d = 2. * mud * mud - 1.;

        /* angle interpolation coordinates */
        Interp.interpCoord(sun_zenith, auxdata.Rayscatt_coeff_s.getTab(2), tsi); /* fm 15/5/97 */
        Interp.interpCoord(view_zenith, auxdata.Rayscatt_coeff_s.getTab(3), tvi);

        float[][][][] Rayscatt_coeff_s = (float[][][][]) auxdata.Rayscatt_coeff_s.getJavaArray();
        /* pre-computation of multiple scatt coefficients, wavelength independent */
        for (int is = 0; is < RAYSCATT_NUM_SER; is++) {
            /* DPM #2.1.17-4 to 2.1.17-7 */
            for (int ik = 0; ik < RAYSCATT_NUM_ORD; ik++) {
                lh.abcd[is][ik] = Interp.interpolate(Rayscatt_coeff_s[ik][is], lh.ref_rayleigh_i);
            }
        }

        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
            if (bandId != LandsatConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX && bandId != LandsatConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX) {
                double constTerm = (1. - Math.exp(-tauRayl[bandId] * airMass)) / (4. * (mus + muv));
                for (int is = 0; is < RAYSCATT_NUM_SER; is++) {
                    /* primary scattering reflectance */
                    lh.rhoRayl[is] = phaseRayl[is] * constTerm; /* DPM #2.1.17-8 CORRECTED */

                    /* coefficient for multiple scattering correction */
                    double multiScatteringCoeff = 0.;
                    for (int ik = RAYSCATT_NUM_ORD - 1; ik >= 0; ik--) {
                        multiScatteringCoeff = tauRayl[bandId] * multiScatteringCoeff + lh.abcd[is][ik]; /* DPM #2.1.17.9 */
                    }

                    /* Fourier component of Rayleigh reflectance */
                    lh.rhoRayl[is] *= multiScatteringCoeff; /* DPM #2.1.17-10 */
                }

                /* Rayleigh reflectance */
                refRayl[bandId] = lh.rhoRayl[0] +
                        2. * mud * lh.rhoRayl[1] +
                        2. * mu2d * lh.rhoRayl[2]; /* DPM #2.1.17-11 */
            } else {
                /* TM6 - no correction */
                refRayl[bandId] = 0.;
            }
        } /* end loop on bands */
    }

    /**
     * Computes three Fourier components of Rayleigh function.
     * <p/>
     * <p/>
     * <b>Input:</b> all arguments and {@link org.esa.beam.meris.l2auxdata.L2AuxData#AB}<br> <b>Output:</b>
     * <code>phaseRayl</code><br> <b>DPM ref.:</b> DPM L2, section 7.3.3.3.2 <br> <b>MEGS ref.:</b>
     * <code>ray_cor.c</code>, function <code>phase_rayleigh</code><br>
     *
     * @param mus       cosine of sun zenith angle
     * @param muv       cosine of view zenith angle
     * @param sins      sine of sun zenith angle
     * @param sinv      sine of view zenith angle
     * @param phaseRayl Fourier components of Rayleigh phase function
     */
    public void phase_rayleigh(double mus, double muv,
                               double sins, double sinv,
                               double[] phaseRayl) {
        phaseRayl[0] = .75 * auxdata.AB[0] * (1. + mus * mus * muv * muv +
                .5 * sins * sins * sinv * sinv) + auxdata.AB[1]; /* DPM #2.1.17-1 corrected */
        phaseRayl[1] = -0.75 * auxdata.AB[0] * mus * muv * sins * sinv; /* DPM #2.1.17-2 corrected */
        phaseRayl[2] = 0.1875 * auxdata.AB[0] * sins * sins * sinv * sinv; /* DPM #2.1.17.3 corrected */
    }

    /**
     * Computes Rayleigh optical thickness.
     * <p/>
     * <b>Input:</b> variable <code>press</code>, {@link org.esa.beam.meris.l2auxdata.L2AuxData#Pstd},{@link
     * org.esa.beam.meris.l2auxdata.L2AuxData#tau_R} <br> <b>Output:</b> <code>tauRayl</code> <br> <b>DPM
     * ref.:</b> L2 DPM section 7.3.3.3.3.2 <br> <b>MEGS ref.:</b> <code>ray_cor.c</code>, function
     * <code>tau_rayleigh</code><br>
     *
     * @param press   average pressure in 4x4 window (P_4x4)
     * @param tauRayl rayleigh opt. thick (tauR0)
     */
    public void tau_rayleigh(double press, double[] tauRayl) {
        double ratio = press / auxdata.Pstd;

        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
            if (bandId != LandsatConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX  && bandId != LandsatConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX) {
                tauRayl[bandId] = auxdata.tau_R[bandId] * ratio; /* DPM #2.6.15.2-5 */
            } else {
                /* TM6 - no correction */
                tauRayl[bandId] = 0.;
            }
        }
    }

    /*-----------------------------------------------------------------------------*\
 * Function trans_rayleigh: compute Rayleigh transmittance
 * for all bands for a given zenith angle
 * inputs:
 *   mu            cosine of zenith angle
 *   tauRayl       Rayleigh optical thickness
 *   Raytrans      transmittance correction coeffs
 * outputs:
 *   transRayl     Rayleigh transmittance for all bands
 * Reference: DPM L2, section 7.3.3.3.2
 * called by: landAtmCor
\*-----------------------------------------------------------------------------*/

    public void trans_rayleigh(double mu, double[] tauRayl, double[] transRayl) {

        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
            if (bandId != LandsatConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX  && bandId != LandsatConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX) {
                double tr = (2. / 3. + mu + (2. / 3. - mu) * Math.exp(-tauRayl[bandId] / mu))
                        / (4. / 3. + tauRayl[bandId]); /* DPM #2.6.15.2-1, -3 */
                transRayl[bandId] = auxdata.Raytrans[0] + auxdata.Raytrans[1] * tr
                        + auxdata.Raytrans[2] * tr * tr; /* DPM #2.6.15.2-2, -4 */
            } else {
                /* TM6 - no correction */
                transRayl[bandId] = 1.;
            }
        }
        return;
    }
    /*-----------------------------------------------------------------------------*\
     * Function sphalb_rayleigh: compute Rayleigh spherical albedo
     * for all bands
     * inputs:
     *   tauRayl       Rayleigh optical thickness
     *   Rayalb        Rayleigh spherical albedo LUT (global)
     * outputs:
     *   sphalbRayl     Rayleigh spherical albedo for all bands
     * Reference: DPM L2, section 7.3.3.3.2
     * called by: landAtmCor
     * calls:
     *    InterpCoord
     *    GenericInterp
    \*-----------------------------------------------------------------------------*/

    public void sphAlb_rayleigh(double[] tauRayl, double[] sphalbRayl) {

        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
            if (bandId != LandsatConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX  && bandId != LandsatConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX) {
                Interp.interpCoord(tauRayl[bandId], auxdata.Rayalb.getTab(0), lh.ray_index[0]);
                sphalbRayl[bandId] = Interp.interpolate(auxdata.Rayalb.getJavaArray(), lh.ray_index); /* DPM #2.6.15.3-1 */
            } else {
                /* TM6 - no correction */
                sphalbRayl[bandId] = 0.;
            }
        }
    }

    /*-----------------------------------------------------------------------------*\
     * Function corr_rayleigh: compute Rayleigh correction for a pixel
     * for all bands
     * inputs:
     *   refRayl       Rayleigh reflectance
     *   sphalbRayl    Rayleigh spherical albedo
     *   transRs       Rayleigh transmittance (down)
     *   transRv       Rayleigh transmittance (up)
     *   rho           reflectance (uncorrected)
     * outputs:
     *   rho_ag        reflectance (corrected)
     * Reference: DPM L2, section 7.3.3.3.2
     * called by: landAtmCor
    \*-----------------------------------------------------------------------------*/

    public void corr_rayleigh(double[] refRayl, double[] sphalbRayl, double[] transRs, double[] transRv,
                              Tile[] rhoNg, Tile[] brr, int x, int y) {

        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
            if (bandId != LandsatConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX  && bandId != LandsatConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX) {
                double dum = (rhoNg[bandId].getSampleFloat(x, y) - refRayl[bandId]) / (transRs[bandId] * transRv[bandId]);      /* DPM 2.6.15.4-5 */
                brr[bandId].setSample(x, y, dum / (1. + sphalbRayl[bandId] * dum)); /* DPM 2.6.15.4-6 */
            } else {
                /* TM6 - no correction */
            }
        }
    }

    public static class LocalHelperVariables {
        /**
         * Rayleigh reflectance Fourier components. Local helper variable used in {@link EtmRayleighCorrection#ref_rayleigh}.
         */
        private final double[] rhoRayl = new double[RAYSCATT_NUM_SER];
        /**
         * Polynomial coeff for computation, a(s) in DPM. Local helper variable used in {@link EtmRayleighCorrection#ref_rayleigh}.
         */
        private final double[][] abcd = new double[RAYSCATT_NUM_SER][RAYSCATT_NUM_ORD];
        /**
         * Interp. coordinates into table {@link org.esa.beam.meris.l2auxdata.L2AuxData#Rayscatt_coeff_s}. Local
         * helper variable used in {@link EtmRayleighCorrection#ref_rayleigh}.
         */
        private final FractIndex[] ref_rayleigh_i = FractIndex.createArray(2);
        private final FractIndex[] ray_index = FractIndex.createArray(1);

        public FractIndex[] getRef_rayleigh_i() {
            return ref_rayleigh_i;
        }

        public FractIndex[] getRay_index() {
            return ray_index;
        }

        public double[] getRhoRayl() {
            return rhoRayl;
        }

        public double[][] getAbcd() {
            return abcd;
        }

    }

    public LocalHelperVariables getLh() {
        return lh;
    }

    public L2AuxData getAuxdata() {
        return auxdata;
    }
}
