package mitl.projection;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKTReader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UTMProjection {

    private static final double FLATTENING = 1 / 298.257223563;
    private static final double a = 6378.137; // EQUATORIAL_RADIUS in km
    private static final double k_0 = 0.9996;
    private static final double E_0 = 500.000; // in km
    private static final double S_HEMI_N_0 = 10000.000; // in km
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

        // km to m
        utmCoordinates[0] *= 1000;
        utmCoordinates[1] *= 1000;

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

    private double sumEtaPrimePart(double beta, int j, double xi, double eta) {
        return beta * Math.sin(2 * j * eta) * Math.cosh(2 * j * xi);
    }

    private double sumXiPrimePart(double beta, int j, double xi, double eta) {
        return beta * Math.cos(2 * j * eta) * Math.sinh(2 * j * xi);
    }

    private double sumSigmaPrimePart(double beta, int j, double xi, double eta) {
        return 2 * j * beta * Math.cos(2 * j * eta) * Math.cosh(2 * j * xi);
    }

    private double sumLatinTPrimePart(double beta, int j, double xi, double eta) {
        return 2 * j * beta * Math.sin(2 * j * eta) * Math.sinh(2 * j * xi);
    }

    public double[] inverseProject(double x, double y, int zone, Connection connection) {
        // Use PostGIS functionality to get
        String sql = "SELECT ST_AsText(ST_Transform(ST_GeomFromText('POINT(' || ? || ' ' || ? || ')', ?), ?)) AS geom_wkt";

        Point latLongPoint = new Point(new Coordinate(0.0, 0.0), new PrecisionModel(PrecisionModel.FLOATING), 4326);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setDouble(1, x);
            preparedStatement.setDouble(2, y);
            preparedStatement.setInt(3, Integer.parseInt("326" + zone)); // From UTM 326XX
            preparedStatement.setInt(4, 4326); // To WGS84

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    String geom_wkt = resultSet.getString("geom_wkt");
                    System.out.println(geom_wkt);
                    latLongPoint = (Point) new WKTReader().read(geom_wkt);
                }
            } catch (ParseException e) {
                e.printStackTrace();
                System.out.println("failpoint 1");
                return new double[]{0.0, 0.0};
            }

        } catch (SQLException e) {
            System.out.println("failpoint 2");
            e.printStackTrace();
            return new double[]{0.0, 0.0};
        }

        System.out.println("Lat Coord: " + latLongPoint.getY());
        System.out.println("Long Coord: " + latLongPoint.getX());

        return new double[]{latLongPoint.getY(), latLongPoint.getX()};

        /* double[] latLongCoords = new double[2];

        double N_0 = 0.0;

        if (!north) {
            N_0 = S_HEMI_N_0;
        }

        double xi = (y - N_0) / k_0 * A;
        double eta = (x - E_0) / k_0 * A;

        double etaPrime = eta - (sumEtaPrimePart(beta_1, 1, xi, eta) + sumEtaPrimePart(beta_2, 2, xi, eta) + sumEtaPrimePart(beta_3, 3, xi, eta));
        double xiPrime = xi - (sumXiPrimePart(beta_1, 1, xi, eta) + sumXiPrimePart(beta_2, 2, xi, eta) + sumXiPrimePart(beta_3, 3, xi, eta));
        double sigmaPrime = 1 - (sumSigmaPrimePart(beta_1, 1, xi, eta) + sumSigmaPrimePart(beta_2, 2, xi, eta) + sumSigmaPrimePart(beta_3, 3, xi, eta));
        double latinTPrime = sumLatinTPrimePart(beta_1, 1, xi, eta) + sumLatinTPrimePart(beta_2, 2, xi, eta) + sumLatinTPrimePart(beta_3, 3, xi, eta);
        double X = Math.asin(Math.sin(etaPrime) / Math.cosh(xiPrime));

        latLongCoords[0] = X + delta_1 * Math.sin(2 * X) + delta_2 * Math.sin(4 * X) + delta_3 * Math.sin(6 * X);
        double centralMeridian = zone * 6 - 183;
        latLongCoords[1] = centralMeridian + Math.atan2(Math.sinh(xiPrime), Math.cos(etaPrime));

        return latLongCoords; */
    }
}
