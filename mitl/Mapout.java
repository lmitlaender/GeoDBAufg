package mitl;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTWriter;
import fu.geo.Spherical;
import fu.keys.LSIClassCentreDB;
import fu.util.DBUtil;
import mitl.projection.UTMProjection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class Mapout {

    private static GeometryFactory geomfact = new GeometryFactory();
    private static UTMProjection utmProjection = new UTMProjection();
    private static Painter mapPainter;

    private static double meterPerPixel = 0;

    private static double offsetX = 0;
    private static double offsetY = 0;


    public static void main(String[] args) {
        System.out.println("Mapout class is running");
        Connection connection=null;

        if (args.length != 6) {
            System.out.println("False number of args. Supply in order: Latitude, Longitude, x, y, meter_x, filename");
            System.exit(1);
        }

        double lat = Double.parseDouble(args[0]);
        double lon = Double.parseDouble(args[1]);
        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);
        double meter_x = Double.parseDouble(args[4]);
        String filename = args[5];
        meterPerPixel = meter_x / x;

        int cnt;
        ResultSet resultSet;
        Statement statement;

        try {
            /* Zugang zur Datenbank einrichten */
            DBUtil.parseDBparams("127.0.0.1/5432/dbuser/dbuser/deproDBMittelfrankenPG",0);
            connection=DBUtil.getConnection(0);
            connection.setAutoCommit(false);  //Getting results based on a cursor
            LSIClassCentreDB.initFromDB(connection);
        }
        catch (Exception e) {
            System.out.println("Error initialising DB access: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }


        Geometry queryGeometry = get_query_geometry(lat, lon, x, y, meter_x, connection);
        System.out.println(new WKTWriter().writeFormatted(queryGeometry));

        mapPainter = new Painter(x, y, meterPerPixel, offsetX, offsetY);

        try {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT realname, lsiclass1, ST_AsEWKB(geom :: geometry)
                    FROM domain WHERE ST_Intersects(geom :: geometry, ST_GeomFromText(?,4326))
                    ORDER BY ST_Length(geom) DESC
                    """
            );

            int col=1;
            preparedStatement.setString(col++, new WKTWriter().write(queryGeometry));

            System.out.println("Querying with "+preparedStatement.toString());

            preparedStatement.setFetchSize(1000);

            resultSet = preparedStatement.executeQuery();

            cnt = 0;

            while (resultSet.next()) {
                col = 1;
                String realname = resultSet.getString(col++);
                int lsiClass = resultSet.getInt(col++);
                byte[] geomdata = resultSet.getBytes(col++);
                Geometry geom = new WKBReader().read(geomdata);

                Geometry projectedGeometry = utmProjection.projectGeometry(geom);

                mapPainter.paintLSIClass(lsiClass, projectedGeometry);


                cnt++;
            }
            resultSet.close();

            System.out.println("Schreibe Bild ...");
            mapPainter.saveImage(filename);
            System.out.println("Bild geschrieben");

        }
        catch (Exception e) {
            System.out.println("Error processing DB queries: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static Geometry get_query_geometry(double lat, double lon, int mapX, int mapY, double meterX, Connection connection) {
        Coordinate[] coords = new Coordinate[5];

        double halfMeterX = meterX / 2;
        double meterY = meterPerPixel * mapY;
        double halfMeterY = meterY / 2;

        System.out.println(halfMeterX + " " + halfMeterY);

        double[] utmCenter = utmProjection.project(lat, lon);
        int zone = utmProjection.getZone(lon);
        double[] invLeftTop = utmProjection.inverseProject(utmCenter[0] - halfMeterX, utmCenter[1] - halfMeterY, zone, connection);
        double[] invRightTop = utmProjection.inverseProject(utmCenter[0] + halfMeterX, utmCenter[1] - halfMeterY, zone, connection);
        double[] invRightBottom = utmProjection.inverseProject(utmCenter[0] + halfMeterX, utmCenter[1] + halfMeterY, zone, connection);
        double[] invLeftBottom = utmProjection.inverseProject(utmCenter[0] - halfMeterX, utmCenter[1] + halfMeterY, zone, connection);

        offsetX = utmCenter[0] - halfMeterX;
        offsetY = utmCenter[1] - halfMeterY;

        coords[0] = new Coordinate(invLeftTop[1], invLeftTop[0]);
        coords[1] = new Coordinate(invRightTop[1], invRightTop[0]);
        coords[2] = new Coordinate(invRightBottom[1], invRightBottom[0]);
        coords[3] = new Coordinate(invLeftBottom[1], invLeftBottom[0]);
        coords[4] = coords[0];

        return geomfact.createPolygon(geomfact.createLinearRing(coords), new LinearRing[0]);
    }
    public static void paintGeometry(Graphics2D g,Geometry geom,String name,String icon) throws IOException {

    }
}
