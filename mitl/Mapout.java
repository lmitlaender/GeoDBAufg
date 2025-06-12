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
import java.util.ArrayList;
import java.util.Arrays;

import static mitl.LSIMapper.PaintType.Unspecified0Building;

public class Mapout {

    private static GeometryFactory geomfact = new GeometryFactory();
    private static UTMProjection utmProjection = new UTMProjection();
    private static Painter mapPainter;

    private static double meterPerPixel = 0;

    private static double offsetX = 0;
    private static double offsetY = 0;


    public static void main(String[] args) {
        System.out.println("Mapout class is running");

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

        Connection connection = DeproDBHelper.getMainConnection();

        Geometry queryGeometry = get_query_geometry(lat, lon, x, y, meter_x, connection);
        System.out.println(new WKTWriter().writeFormatted(queryGeometry));

        mapPainter = new Painter(x, y, meterPerPixel, offsetX, offsetY);

        try {
            ResultSet resultSet = DeproDBHelper.getMainResultSet(queryGeometry);

            if (resultSet == null) {
                System.out.println("ResultSet of main query is null - no objects to draw");
                mapPainter.saveImage(filename);
                System.exit(1);
            }

            while (resultSet.next()) {
                int col = 1;
                int d_id = resultSet.getInt(col++);
                String realname = resultSet.getString(col++);
                int lsiClass = resultSet.getInt(col++);
                int lsiClass2 = resultSet.getInt(col++);
                int lsiClass3 = resultSet.getInt(col++);
                String tags = resultSet.getString(col++);
                byte[] geomdata = resultSet.getBytes(col++);
                Geometry geom = new WKBReader().read(geomdata);

                Geometry projectedGeometry = utmProjection.projectGeometry(geom);

                if (tags != null && tags.contains("level=")) {
                    // Get level number between "level=" and ","
                    String level = tags.substring(tags.indexOf("level=") + 6);
                    if (level.contains(",")) {
                        level = level.substring(0, level.indexOf(","));
                    }
                    // Level might now be a list of levels "1;2;3", we want the highest one
                    String[] levels = level.split(";");
                    int levelNumber = -999;
                    for (String l : levels) {
                        try {
                            int lnum = Integer.parseInt(l);
                            if (lnum > levelNumber) {
                                levelNumber = lnum;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore non-numeric levels
                        }
                    }

                    // If it is a negative level and a polygon we represent it as a point object so that underground shops get marked, but not drawn.
                    if (levelNumber < 0 && projectedGeometry instanceof Polygon) {
                        System.out.println("Repurposing underground polygon (realname: " + realname + ", level: " + levelNumber + ", lsiClass1: " + LSIClassCentreDB.className(lsiClass) + ") to point object for marking only.");
                        // Convert polygon to point by taking the centroid
                        projectedGeometry = projectedGeometry.getCentroid();
                        // Set the geometry type to point
                        projectedGeometry.setUserData("Point");
                    }
                }

                // if building=yes in tags, then paint it as a building - handles cases where both school and school area is done as same lsicode
                var checkToDoList = new ArrayList<Integer>();
                checkToDoList.add(0);
                checkToDoList.addAll(LSIMapper.getLSICodeList(LSIClassCentreDB.lsiClass("EDUCATION"), true));
                checkToDoList.add(LSIClassCentreDB.lsiClass("FEUERWEHR"));
                checkToDoList.add(LSIClassCentreDB.lsiClass("POLIZEI"));
                checkToDoList.addAll(LSIMapper.getLSICodeList(LSIClassCentreDB.lsiClass("BETREUUNG_KINDER"), true));
                if (tags != null && checkToDoList.contains(lsiClass) && tags.contains("building=train_station")) {
                    mapPainter.paintLSIClass(d_id, lsiClass, lsiClass2, lsiClass3, projectedGeometry, realname, LSIMapper.PaintType.TrainStation);
                }
                else if (tags != null && checkToDoList.contains(lsiClass) && (tags.contains("building="))) {
                    mapPainter.paintLSIClass(d_id, lsiClass, lsiClass2, lsiClass3, projectedGeometry, realname, Unspecified0Building);
                } else if (tags != null && tags.contains("man_made=bridge")) {
                    // Recognize Bridge Polygons based on tag
                    mapPainter.paintLSIClass(d_id, lsiClass, lsiClass2, lsiClass3, projectedGeometry, realname, LSIMapper.PaintType.Bridge);
                } else if (tags != null && tags.contains("landuse=retail")) {
                    // Workaround for retail landuse polygons that are falsely classified as SHOP.
                    // landuse=retail should only be used for areas, not for single shops according to OSM specification, as such this should not impact real shops.
                    System.out.println("Recognizing retail landuse for " + realname);
                    mapPainter.paintLSIClass(d_id, LSIClassCentreDB.lsiClass("COMMERCIAL"), lsiClass2, lsiClass3, projectedGeometry, realname, null);
                }
                else
                {
                    mapPainter.paintLSIClass(d_id, lsiClass, lsiClass2, lsiClass3, projectedGeometry, realname, null);
                }
            }
            resultSet.close();

            System.out.println("Writing image ...");
            mapPainter.saveImage(filename);
            System.out.println("Image written to " + filename);

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
