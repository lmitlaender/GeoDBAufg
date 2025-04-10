package mitl.projection;

public class UTMProjection {

    private static final double FLATTENING = 1 / 298.257223563;
    private static final double a = 6378137.0; // EQUATORIAL_RADIUS in meters
    private static final double k_0 = 0.9996;
    private static final double E_0 = 500000.0; // in meters
    private static final double S_HEMI_N_0 = 10000000.0; // in meters
    private static final double n = FLATTENING / (2 - FLATTENING);
    private static final double A = a / (1 + n) * (1 + n * n / 4 + n * n * n * n / 64);
    private static final double alpha_1 = 0.5 * n - 2.0 / 3.0 * n * n + 5.0 / 16.0 * n * n * n;
    private static final double alpha_2 = 13.0 / 48.0 * n * n - 3.0 / 5.0 * n * n * n;
    private static final double alpha_3 = 61.0 / 240.0 * n * n * n;
    private static final double beta_1 = 0.5 * n - 2.0 / 3.0 * n * n + 37.0 / 96.0 * n * n * n;
    private static final double beta_2 = 1.0 / 48.0 * n * n + 1.0 / 15.0 * n * n * n;
    private static final double beta_3 = 17.0 / 480.0 * n * n * n;
    private static final double delta_1 = 2.0 * n - 2.0 / 3.0 * n * n - 2.0 * n * n * n;
    private static final double delta_2 = 7.0 / 3.0 * n * n - 8.0 / 5.0 * n * n * n;
    private static final double delta_3 = 56.0 / 15.0 * n * n * n;

    private static double atanh(double x) {
        return 0.5 * Math.log((1 + x) / (1 - x));
    }

    private static double sum_sigma_part(double alpha, int j, double xi, double eta) {
        return 2 * j * alpha * Math.cos(2 * j * xi) * Math.cosh(2 * j * eta);
    }
    private static double sum_latin_t_part(double alpha, int j, double xi, double eta) {
        return 2 * j * alpha * Math.sin(2 * j * xi) * Math.sinh(2 * j * eta);
    }

    private static double sum_easting_part(double alpha, int j, double xi, double eta) {
        return alpha * Math.cos(2 * j * xi) * Math.sinh(2 * j * eta);
    }

    private static double sum_northing_part(double alpha, int j, double xi, double eta) {
        return alpha * Math.sin(2 * j * xi) * Math.cosh(2 * j * eta);
    }

    public double[] project(double lat_deg, double lon_deg) {
        // Convert latitude and longitude to UTM coordinates
        double[] utmCoordinates = new double[2];
        double lat = lat_deg * Math.PI / 180;
        double lon = lon_deg * Math.PI / 180;

        int zone = getZone(lon_deg);
        System.out.println("Zone: " + zone);
        double centralMeridian = getCentralMeridianRad(zone);
        System.out.println("centralMeridian: " + centralMeridian);

        // No atanh in Math sadly - replace with 0.5ln((1 + x) / (1 - x))
        double t = Math.sinh(
                atanh(Math.sin(lat)) - (2 * Math.sqrt(n)) / (1 + n) * atanh((2 * Math.sqrt(n)) / (1 + n) * Math.sin(lat))
        );
        double xi = Math.atan2(t, Math.cos(lon - centralMeridian));
        double eta = atanh(Math.sin(lon - centralMeridian) / Math.sqrt(1 + t * t));
        double sigma = 1 + sum_sigma_part(alpha_1, 1, xi, eta) + sum_sigma_part(alpha_2, 2, xi, eta) + sum_sigma_part(alpha_3, 3, xi, eta);
        double latin_t = sum_latin_t_part(alpha_1, 1, xi, eta) + sum_latin_t_part(alpha_2, 2, xi, eta) + sum_latin_t_part(alpha_3, 3, xi, eta);

        utmCoordinates[0] = E_0 + k_0 * A * (eta + sum_easting_part(alpha_1, 1, xi, eta) + sum_easting_part(alpha_2, 2, xi, eta) + sum_easting_part(alpha_3, 3, xi, eta));
        if (lat_deg >= 0.0) {
            utmCoordinates[1] = 0.0 + k_0 * A * (xi + sum_northing_part(alpha_1, 1, xi, eta) + sum_northing_part(alpha_2, 2, xi, eta) + sum_northing_part(alpha_3, 3, xi, eta));
        } else {
            utmCoordinates[1] = S_HEMI_N_0 + k_0 * A * (xi + sum_northing_part(alpha_1, 1, xi, eta) + sum_northing_part(alpha_2, 2, xi, eta) + sum_northing_part(alpha_3, 3, xi, eta));
        }

        System.out.println("UTM Coord: " + utmCoordinates[0]);
        System.out.println("UTM Coord: " + utmCoordinates[1]);

        return utmCoordinates;
    }

    public int getZone(double lon) {
        // Calculate the UTM zone based on longitude
        return (int) ((lon + 180) / 6) + 1;
    }

    private double getCentralMeridianRad(int zone) {
        // Calculate the central meridian for the given UTM zone
        return ((zone - 1) * 6 - 180 + 3) * Math.PI / 180;
    }

    private double getFlattening() {
        return 1 / 298.257223563;
    }

    public double[] inverseProject(double x, double y, int zone, boolean north) {
        return new double[0];
    }
}
