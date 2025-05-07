package mitl;

import com.vividsolutions.jts.geom.*;
import fu.keys.LSIClassCentreDB;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

public class Painter {
    private HashMap<Integer, BufferedImage> zImages = new HashMap<>();
    private HashMap<Integer, Graphics2D> gLayers = new HashMap<>();

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
        AUFFAHRT(Color.GRAY, 3),
        ZUFAHRTPARKPLATZWEG(Color.GRAY, 2);

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

        var layer0 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var gLayer0 = (Graphics2D) layer0.getGraphics();
        gLayer0.setColor(Color.WHITE);
        gLayer0.fillRect(0, 0, width, height);
        gLayers.put(0, gLayer0);
        zImages.put(0, layer0);
    }

    public void saveImage(String filename) {
        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gFinal = (Graphics2D) finalImage.getGraphics();
        gFinal.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten

        // Sort the layers by their keys
        Integer[] keys = gLayers.keySet().toArray(new Integer[0]);
        Arrays.sort(keys);
        for (int i = 0; i < keys.length; i++) {
            BufferedImage layer = zImages.get(keys[i]);
            if (layer != null) {
                gFinal.drawImage(layer, 0, 0, null);
            }
        }

        try {
            ImageIO.write(finalImage, "png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void paintLSIClass(int lsiClass, Geometry geom, String name, LSIMapper.PaintType paintType) {
        if (paintType == null) {
            paintType = LSIMapper.lsiCodeToPaintType(lsiClass);
        }

        if (paintType == null) {
            //System.out.println("Unknown LSI code: " + lsiClass);
            return;
        }
        int z = paintType.getZ();

        switch (paintType) {
            case Autobahn -> drawStreet(geom, StreetCategory.AUTOBAHN, name);
            case Kraftfahrstrasse -> drawStreet(geom, StreetCategory.KRAFTFAHRSTRASSE, name);
            case StandardStrasse -> drawStreet(geom, StreetCategory.STANDARD_STRASSE, name);
            case FeldWaldWeg -> drawStreet(geom, StreetCategory.FELD_WALD_WEG, name);
            case Auffahrt -> drawStreet(geom, StreetCategory.AUFFAHRT, name);
            case AdditionalSmallRoads -> drawStreet(geom, StreetCategory.ZUFAHRTPARKPLATZWEG, name);
            case Rail -> drawGeometryBasedOnType(z, geom, Color.lightGray, 1, null);
            case GeneralGreen, Naherholungsgebiet -> drawGeometryBasedOnType(z, geom, new Color(11, 156, 49, 51), 5, null);
            case Forest -> drawGeometryBasedOnType(z, geom, new Color(11, 156, 49, 200), 5, null);
            case Sportplatz, Fussballplatz -> drawGeometryBasedOnType(z, geom, new Color(136, 224, 190, 255), 5, new Color(89, 147, 125));
            case Playground -> drawGeometryBasedOnType(z, geom, new Color(223, 252, 226, 255), 5, new Color(177, 201, 180));
            case FootCyclePath -> drawGeometryBasedOnType(z, geom, Color.gray, 1, null);
            case PedestrianZone -> drawGeometryBasedOnType(z, geom, new Color(239, 239, 239), 1, new Color(188, 188, 188));
            case Overnight -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case Building, Unspecified0Building -> drawGeometryBasedOnType(z, geom, new Color(217, 208, 201), 3, new Color(197, 187, 177));
            case Water -> drawGeometryBasedOnType(z, geom, Color.BLUE, 3, null);
            case Bridge -> drawGeometryBasedOnType(z, geom, Color.darkGray, 3, null);
            case Religious -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case Cemetery -> drawGeometryBasedOnType(z, geom, new Color(170, 203, 175), 3, new Color(105, 126, 109));
            case MedicalArea -> drawGeometryBasedOnType(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207));
            case EducationArea -> drawGeometryBasedOnType(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207));
            case FireDeparmentArea -> drawGeometryBasedOnType(z, geom, new Color(243, 227, 221), 3, new Color(246, 193, 188));
            case PoliceArea -> drawGeometryBasedOnType(z, geom, new Color(243, 227, 221), 3, new Color(246, 193, 188));
            case Pharmacy -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case ATM -> drawGeometryBasedOnType(z, geom, Color.MAGENTA, 3, new Color(231, 231, 207));
            case FinanceBuilding -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case Theatre, Cinema, ConcertHall, Museum, AnimalInstitutions -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case KindergartenArea -> drawGeometryBasedOnType(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207));
            case ThemeParkArea -> drawGeometryBasedOnType(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207));
            case Court -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case CityHall -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case Gastronomy -> drawGeometryBasedOnType(z, geom, new Color(255, 255, 0, 150), 3, new Color(180, 165, 183));
            case Comercial -> {drawGeometryBasedOnType(z, geom, new Color(0, 0, 255, 150), 3, new Color(180, 165, 183));
            System.out.println(name + ", lsiclass: " + LSIClassCentreDB.className(lsiClass));}
            //default -> System.out.println("Unhandled LSI code: " + lsiClass);
        }
    }

    private void drawStreet(Geometry geom, StreetCategory streetCategory, String name) {
        int width = (int) Math.floor((streetCategory.getWidth() / meterPerPixel) + 1);
        Color color = streetCategory.getColor();

        drawGeometryBasedOnType(LSIMapper.PaintType.StandardStrasse.getZ(), geom, color, width, null);

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

                Shape textShape = getTextShape(9999999, name, 12);
                Shape centeredTextShape = centerTextShape(textShape, midX, midY);
                drawTextShape(9999999, centeredTextShape, Color.WHITE, angle);

                //drawText(9999999, name, midX, midY, Color.WHITE, 12, angle);
            }
        }
    }

    private void drawGeometryBasedOnType(int z, Geometry geom, Color color, int strokeWidth, Color secondaryColor) {
        drawGeometryBasedOnType(z, geom, color, new BasicStroke(strokeWidth), secondaryColor);
    }

    private void drawGeometryBasedOnType(int z, Geometry geom, Color primaryColor, BasicStroke stroke, Color secondaryColor) {
        if (geom instanceof com.vividsolutions.jts.geom.Polygon polygon) {
            drawPolygon(z, polygon, primaryColor, stroke, secondaryColor);
        } else if (geom instanceof com.vividsolutions.jts.geom.LineString lineString) {
            drawLineString(z, lineString, primaryColor, stroke);
        } else if (geom instanceof com.vividsolutions.jts.geom.Point point){
            drawPoint(z, point.getX(), point.getY(), primaryColor, (int) stroke.getLineWidth());
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPoint multiPoint) {
            for (Coordinate coordinate : multiPoint.getCoordinates()) {
                drawPoint(z, coordinate.x, coordinate.y, primaryColor, (int) stroke.getLineWidth());
            }
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiLineString multiLineString) {
            for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                drawLineString(z, (com.vividsolutions.jts.geom.LineString) multiLineString.getGeometryN(i), primaryColor, stroke);
            }
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPolygon multiPolygon) {
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                drawGeometryBasedOnType(z, multiPolygon.getGeometryN(i), primaryColor, stroke, secondaryColor);
            }
        } else {
            System.out.println("Unknown geometry type: " + geom.getClass());
        }
    }

    Graphics2D getGraphicForZ(int z) {
        if (gLayers.containsKey(z)) {
            return gLayers.get(z);
        } else {
            BufferedImage layer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gLayer = (Graphics2D) layer.getGraphics();
            gLayer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten
            zImages.put(z, layer);
            gLayers.put(z, gLayer);
            return gLayer;
        }
    }

    private Shape getTextShape(int z, String text, int fontSize) {
        Graphics2D g = getGraphicForZ(z);
        Font font = new Font("Arial", Font.PLAIN, fontSize);
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, text);
        Shape textShape = gv.getOutline();
        return textShape;
    }

    private Shape centerTextShape(Shape textShape, double x, double y) {
        Rectangle2D bounds = textShape.getBounds2D();

        int[] renderPoint = {
                (int) ((x - offsetX) / meterPerPixel),
                height - (int) ((y - offsetY) / meterPerPixel)
        };

        AffineTransform transform = AffineTransform.getTranslateInstance(
                renderPoint[0] - bounds.getCenterX(),
                renderPoint[1] - bounds.getCenterY()
        );
        return transform.createTransformedShape(textShape);
    }

    private void drawTextShape(int z, Shape textShape, Color color, double angle) {
        Graphics2D g = getGraphicForZ(z);
        g.setColor(color);
        g.setStroke(new BasicStroke(1));

        if (angle < 0) {
            angle += 180;
        }
        if (angle > 90) {
            angle -= 180;
        }

        // Rotate the graphics context
        g.rotate(Math.toRadians(angle), textShape.getBounds2D().getCenterX(), textShape.getBounds2D().getCenterY());
        g.fill(textShape);
        g.rotate(-Math.toRadians(angle), textShape.getBounds2D().getCenterX(), textShape.getBounds2D().getCenterY());
    }

    private void drawText(int z, String text, double x, double y, Color color, int fontSize, double angle) {
        Graphics2D g = getGraphicForZ(z);
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

    private void drawPolygon(int z, com.vividsolutions.jts.geom.Polygon polygon, Color color, Color secondaryColor) {
        drawPolygon(z, polygon, color, new BasicStroke(1), secondaryColor);
    }

    private void drawPolygon(int z, com.vividsolutions.jts.geom.Polygon polygon, Color color, Stroke stroke, Color secondaryColor) {
        Graphics2D g = getGraphicForZ(z);
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

        // Draw outline if secondary color is provided
        if (secondaryColor != null) {
            g.setColor(secondaryColor);
            g.setStroke(new BasicStroke(1));
            g.draw(area);
        }
    }

    private void drawLineString(int z, LineString lineString, Color color, int strokeWidth) {
        drawLineString(z, lineString, color, new BasicStroke(strokeWidth));
    }

    private void drawLineString(int z, LineString lineString, Color color, BasicStroke stroke) {
        Graphics2D g = getGraphicForZ(z);
        g.setColor(color);
        g.setStroke(stroke);

        Path2D path = new Path2D.Double();

        if (stroke.getLineWidth() > 1.0) {
            drawPolygon(z, (com.vividsolutions.jts.geom.Polygon) lineString.buffer(((stroke.getLineWidth() - 1) * meterPerPixel) / 2), color, stroke, null);
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

    private void drawPoint(int z, double pointX, double pointY, Color color, int size) {
        Graphics2D g = getGraphicForZ(z);
        g.setColor(color);
        g.fillOval((int) ((pointX - offsetX) / meterPerPixel), height - (int) ((pointY - offsetY) / meterPerPixel), size, size);
    }
}
