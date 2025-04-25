package mitl;

import com.vividsolutions.jts.awt.PointShapeFactory;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;
import fu.keys.LSIClass;
import fu.keys.LSIClassCentreDB;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class Painter {
    private BufferedImage[] zImages = new BufferedImage[10];
    private Graphics2D[] gLayers = new Graphics2D[10];

    private final int width;
    private final int height;
    private final double meterPerPixel;
    private final double offsetX;
    private final double offsetY;

    public static enum StreetCategory {
        AUTOBAHN(Color.DARK_GRAY, 12),
        KRAFTFAHRSTRASSE(Color.MAGENTA, 8),
        STANDARD_STRASSE(Color.GRAY, 3),
        FELD_WALD_WEG(Color.GRAY, 3),
        AUFFAHRT(Color.GRAY, 3);

        private final Color color;
        private final int width;

        // Constructor that takes a Color and width (in arbitrary units like pixels)
        StreetCategory(Color color, int width) {
            this.color = color;
            this.width = width;
        }
        public Color getColor() {
            return color;
        }
        public int getWidth() {
            return width;
        }
    }

    public Painter(int width, int height, double meterPerPixel, double offsetX, double offsetY) {
        this.width = width;
        this.height = height;
        this.meterPerPixel = meterPerPixel;
        this.offsetX = offsetX;
        this.offsetY = offsetY;

        for (int i = 0; i < zImages.length; i++) {
            zImages[i] = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            gLayers[i] = (Graphics2D) zImages[i].getGraphics();
            gLayers[i].setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten
        }
        gLayers[0].setColor(Color.WHITE);
        gLayers[0].fillRect(0, 0, width, height);
    }

    public void saveImage(String filename) {
        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gFinal = (Graphics2D) finalImage.getGraphics();
        gFinal.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten

        for (int i = 0; i < 10; i++) {
            gFinal.drawImage(zImages[i], 0, 0, null);
        }

        try {
            ImageIO.write(finalImage, "png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void paintLSIClass(int lsiClass, Geometry geom, String name) {
        LSIMapper.PaintType paintType = LSIMapper.lsiCodeToPaintType(lsiClass);

        if (paintType == null) {
            //System.out.println("Unknown LSI code: " + lsiClass);
            return;
        }

        if (lsiClass == LSIClassCentreDB.lsiClass("FUSSGAENGERZONE") && geom instanceof Polygon) {
            // Don't print Fussgaengerzone polygons - we only want internal walking paths
            return;
        }


        switch (paintType) {
            case Autobahn -> drawStreet(geom, StreetCategory.AUTOBAHN, name);
            case Kraftfahrstrasse -> drawStreet(geom, StreetCategory.KRAFTFAHRSTRASSE, name);
            case StandardStrasse -> drawStreet(geom, StreetCategory.STANDARD_STRASSE, name);
            case FeldWaldWeg -> drawStreet(geom, StreetCategory.FELD_WALD_WEG, name);
            case Auffahrt -> drawStreet(geom, StreetCategory.AUFFAHRT, name);
            case Rail -> drawGeometryBasedOnType(gLayers[3], geom, Color.lightGray, 1);
            case Vegetation -> drawGeometryBasedOnType(gLayers[1], geom, new Color(234, 255, 225), 5);
            case FootCyclePath -> drawGeometryBasedOnType(gLayers[3], geom, Color.gray, 1);
            case Overnight -> drawGeometryBasedOnType(gLayers[4], geom, Color.CYAN, 3);
            case Building -> drawGeometryBasedOnType(gLayers[4], geom, Color.MAGENTA, 3);
            case Water -> drawGeometryBasedOnType(gLayers[1], geom, Color.BLUE, 3);
            case Bridge -> drawGeometryBasedOnType(gLayers[2], geom, Color.darkGray, 3);
            //default -> System.out.println("Unhandled LSI code: " + lsiClass);
        }
    }

    private void drawStreet(Geometry geom, StreetCategory streetCategory, String name) {
        int width = (int) Math.floor((streetCategory.getWidth() / meterPerPixel) + 1);
        Color color = streetCategory.getColor();

        drawGeometryBasedOnType(gLayers[3], geom, color, width);

        // Draw text segments if the width is greater than 12
        if (width > 12 && geom instanceof LineString lineString) {
            for (int i = 0; i < lineString.getNumPoints() - 1; i++) {
                Coordinate start = lineString.getCoordinateN(i);
                Coordinate end = lineString.getCoordinateN(i + 1);

                double StartX = start.x;
                double StartY = start.y;
                double EndX = end.x;
                double EndY = end.y;

                double length = Math.sqrt(Math.pow(EndX - StartX, 2) + Math.pow(EndY - StartY, 2)) / meterPerPixel;
                if (length < name.length() * 8) {
                    continue; // Skip if the length is too short
                }

                double angle = -Math.toDegrees(Math.atan2(EndY - StartY, EndX - StartX));

                double midX = (StartX + EndX) / 2;
                double midY = (StartY + EndY) / 2;

                drawText(gLayers[8], name, midX, midY, Color.WHITE, 12, angle);
            }
        }
    }

    private void drawGeometryBasedOnType(Graphics2D g, Geometry geom, Color color, int strokeWidth) {
        drawGeometryBasedOnType(g, geom, color, new BasicStroke(strokeWidth));
    }

    private void drawGeometryBasedOnType(Graphics2D g, Geometry geom, Color color, BasicStroke stroke) {
        if (geom instanceof com.vividsolutions.jts.geom.Polygon polygon) {
            drawPolygon(g, polygon, color, stroke);
        } else if (geom instanceof com.vividsolutions.jts.geom.LineString lineString) {
            drawLineString(g, lineString, color, stroke);
        } else if (geom instanceof com.vividsolutions.jts.geom.Point point){
            drawPoint(g, point.getX(), point.getY(), color, (int) stroke.getLineWidth());
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPoint multiPoint) {
            for (Coordinate coordinate : multiPoint.getCoordinates()) {
                drawPoint(g, coordinate.x, coordinate.y, color, (int) stroke.getLineWidth());
            }
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiLineString multiLineString) {
            for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                drawLineString(g, (com.vividsolutions.jts.geom.LineString) multiLineString.getGeometryN(i), color, stroke);
            }
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPolygon multiPolygon) {
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                drawGeometryBasedOnType(g, multiPolygon.getGeometryN(i), color, stroke);
            }
        } else {
            System.out.println("Unknown geometry type: " + geom.getClass());
        }
    }

    private void drawText(Graphics2D g, String text, double x, double y, Color color, int fontSize, double angle) {
        g.setColor(color);
        g.setFont(new Font("Arial", Font.PLAIN, fontSize));
        int[] renderPoint = {
                (int) ((x - offsetX) / meterPerPixel),
                height - (int) ((y - offsetY) / meterPerPixel)
        };
        if (angle < 0) {
            angle += 180;
        }
        if (angle > 90) {
            angle -= 180;
        }
        renderPoint[1] += fontSize / 2;

        System.out.println("Drawing text: " + text + " with angle: " + angle);

        g.rotate(Math.toRadians(angle), renderPoint[0], renderPoint[1]);
        g.drawString(text, renderPoint[0], renderPoint[1]);
        g.rotate(-Math.toRadians(angle), renderPoint[0], renderPoint[1]);
    }

    private void drawPolygon(Graphics2D g, com.vividsolutions.jts.geom.Polygon polygon, Color color) {
        drawPolygon(g, polygon, color, new BasicStroke(1));
    }

    private void drawPolygon(Graphics2D g, com.vividsolutions.jts.geom.Polygon polygon, Color color, Stroke stroke) {
        g.setColor(color);
        g.setStroke(stroke);

        int[] xPoints = Arrays.stream(polygon.getExteriorRing().getCoordinates()).sequential()
                .mapToInt(coord -> (int) ((coord.x - offsetX) / meterPerPixel))
                .toArray();
        int[] yPoints = Arrays.stream(polygon.getExteriorRing().getCoordinates()).sequential()
                .mapToInt(coord -> height - (int) ((coord.y - offsetY) / meterPerPixel))
                .toArray();

        java.awt.Polygon outerPolygon = new java.awt.Polygon(xPoints, yPoints, polygon.getExteriorRing().getNumPoints());
        Area area = new Area(outerPolygon);

        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            int[] holeXPoints = Arrays.stream(polygon.getInteriorRingN(i).getCoordinates()).sequential()
                    .mapToInt(coord -> (int) ((coord.x - offsetX) / meterPerPixel))
                    .toArray();
            int[] holeYPoints = Arrays.stream(polygon.getInteriorRingN(i).getCoordinates()).sequential()
                    .mapToInt(coord -> height - (int) ((coord.y - offsetY) / meterPerPixel))
                    .toArray();

            java.awt.Polygon holePolygon = new java.awt.Polygon(holeXPoints, holeYPoints, polygon.getInteriorRingN(i).getNumPoints());
            area.subtract(new Area(holePolygon));
        }

        // Fill area
        g.fill(area);
    }

    private void drawLineString(Graphics2D g, LineString lineString, Color color, int strokeWidth) {
        drawLineString(g, lineString, color, new BasicStroke(strokeWidth));
    }

    private void drawLineString(Graphics2D g, LineString lineString, Color color, BasicStroke stroke) {
        g.setColor(color);
        g.setStroke(stroke);

        Path2D path = new Path2D.Double();

        if (stroke.getLineWidth() > 1.0) {
            drawPolygon(g, (com.vividsolutions.jts.geom.Polygon) lineString.buffer(((stroke.getLineWidth() - 1) * meterPerPixel) / 2), color, stroke);
            return;
        }

        for (int i = 0; i < lineString.getNumPoints() - 1; i++) {
            Coordinate start = lineString.getCoordinateN(i);
            Coordinate end = lineString.getCoordinateN(i + 1);

            // Add the start point to the path for the first iteration
            if (i == 0) {
                path.moveTo((start.x - offsetX) / meterPerPixel, height - (start.y - offsetY) / meterPerPixel);
            }

            // Add the line to the path for each subsequent point
            path.lineTo((end.x - offsetX) / meterPerPixel, height - (end.y - offsetY) / meterPerPixel);
        }

        g.draw(path);
    }

    private void drawPoint(Graphics2D g, double pointX, double pointY, Color color, int size) {
        g.setColor(color);
        g.fillOval((int) ((pointX - offsetX) / meterPerPixel), height - (int) ((pointY - offsetY) / meterPerPixel), size, size);
    }
}
