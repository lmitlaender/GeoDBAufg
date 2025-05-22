package mitl;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import fu.keys.LSIClassCentreDB;
import mitl.projection.UTMProjection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class Painter {
    private HashMap<Integer, BufferedImage> zImages = new HashMap<>();
    private HashMap<Integer, Graphics2D> gLayers = new HashMap<>();

    private final int width;
    private final int height;
    private final double meterPerPixel;
    private final double offsetX;
    private final double offsetY;
    private int currentDrawId = 0;

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

    public void paintLSIClass(int d_id, int lsiClass, int lsiClass2, int lsiClass3, Geometry geom, String name, LSIMapper.PaintType paintType) {
        this.currentDrawId = d_id;
        if (paintType == null) {
            paintType = LSIMapper.lsiCodeToPaintType(lsiClass);
        }
        LSIMapper.PaintType paintType2 = LSIMapper.lsiCodeToPaintType(lsiClass2);
        LSIMapper.PaintType paintType3 = LSIMapper.lsiCodeToPaintType(lsiClass3);

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
            case Rail -> drawGeometryBasedOnType(z, geom, Color.lightGray, new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {4,2,8,2}, 0), null);
            case GeneralGreen, Naherholungsgebiet -> drawGeometryBasedOnType(z, geom, new Color(11, 156, 49, 51), 5, null);
            case Forest -> drawGeometryBasedOnType(z, geom, new Color(11, 156, 49, 200), 5, null);
            case Sportplatz, Fussballplatz -> drawGeometryBasedOnType(z, geom, new Color(136, 224, 190, 255), 5, new Color(89, 147, 125));
            case Playground -> drawGeometryBasedOnType(z, geom, new Color(223, 252, 226, 255), 5, new Color(177, 201, 180));
            case FootCyclePath -> drawGeometryBasedOnType(z, geom, Color.gray, 1, null);
            case PedestrianZone -> drawSpecialArea(z, geom, new Color(239, 239, 239), 1, new Color(188, 188, 188), "pedestrianzone", name);
            case Overnight -> drawGeometryBasedOnType(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183));
            case Building, Unspecified0Building -> drawGeometryBasedOnType(z, geom, new Color(217, 208, 201), 3, new Color(197, 187, 177));
            case Water -> drawGeometryBasedOnType(z, geom, Color.BLUE, 3, null);
            case Bridge -> {
                drawBridge(z, geom, new Color(184, 184, 184), 3, null, paintTypeToStreetCategory(paintType2));
                System.out.println("Bridge: " + name + ", paintType2: " + paintTypeToStreetCategory(paintType2));
                if (paintType2 != null) {
                    paintLSIClass(d_id, lsiClass2, 0, 0, geom, name, paintType2);
                }
            }
            case Religious -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "religious", name);
            case Cemetery -> drawSpecialArea(z, geom, new Color(170, 203, 175), 3, new Color(105, 126, 109), "graveyard", name);
            case MedicalArea -> drawSpecialArea(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207), "hospital2", name);
            case EducationArea -> drawSpecialArea(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207), "school", name);
            case UniversityArea -> drawSpecialArea(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207), "university", name);
            case FireDeparmentArea -> drawSpecialArea(z, geom, new Color(243, 227, 221), 3, new Color(246, 193, 188), "firedepartment", name);
            case PoliceArea -> drawSpecialArea(z, geom, new Color(243, 227, 221), 3, new Color(246, 193, 188), "police", name);
            case Pharmacy -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "pharmacy", name);
            case ATM -> drawGeometryBasedOnType(z, geom, Color.MAGENTA, 3, new Color(231, 231, 207));
            case FinanceBuilding -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "bank", name);
            case Theatre, Cinema, ConcertHall, Museum, AnimalInstitutions, CommunityLife -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "social", name);
            case KindergartenArea -> drawSpecialArea(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207), "kindergarten", name);
            case ThemeParkArea -> drawSpecialArea(z, geom, new Color(255, 255, 220), 3, new Color(231, 231, 207), "social", name);
            case Court -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "court", name);
            case CityHall -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "tower", name);
            case Gastronomy -> drawGeometryBasedOnType(z, geom, new Color(255, 255, 0, 150), 3, new Color(180, 165, 183));
            case Comercial -> {drawGeometryBasedOnType(z, geom, new Color(0, 0, 255, 150), 3, new Color(180, 165, 183));
            System.out.println(name + ", lsiclass: " + LSIClassCentreDB.className(lsiClass));}
            case SwimmingAll -> {
                System.out.println("SwimmingAll: " + name + ", lsiclass: " + LSIClassCentreDB.className(lsiClass));
                drawGeometryBasedOnType(z, geom, new Color(194, 237, 255), 3, new Color(155, 189, 204));
            }
            case RailPlatform -> drawGeometryBasedOnType(z, geom, new Color(187, 187, 187), 1, new Color(110, 110, 110));
            case Sand -> drawGeometryBasedOnType(z, geom, new Color(251, 236, 183), 3, new Color(199, 188, 145));
            case Tower -> drawSpecialArea(z, geom, new Color(196, 182, 171), 3, new Color(180, 165, 183), "tower", name);
            case HistoricOthersArea -> drawSpecialArea(z, geom, new Color(0, 0, 0, 0), 3, new Color(0, 0, 0, 0), "tower", name);
            //default -> System.out.println("Unhandled LSI code: " + lsiClass);
        }
    }

    private StreetCategory paintTypeToStreetCategory(LSIMapper.PaintType paintType) {
        if (paintType == null) {
            return null;
        }
        return switch (paintType) {
            case Autobahn -> StreetCategory.AUTOBAHN;
            case Kraftfahrstrasse -> StreetCategory.KRAFTFAHRSTRASSE;
            case StandardStrasse -> StreetCategory.STANDARD_STRASSE;
            case FeldWaldWeg -> StreetCategory.FELD_WALD_WEG;
            case Auffahrt -> StreetCategory.AUFFAHRT;
            case AdditionalSmallRoads -> StreetCategory.ZUFAHRTPARKPLATZWEG;
            default -> null;
        };
    }

    private void drawSpecialArea(int z, Geometry geom, Color color, int strokeWidth, Color secondaryColor, String icon, String name) {
        drawGeometryBasedOnType(z, geom, color, new BasicStroke(strokeWidth), secondaryColor);

        // Filters out for example inner paths or the like
        if (!(geom instanceof com.vividsolutions.jts.geom.Polygon polygon || geom instanceof com.vividsolutions.jts.geom.MultiPolygon multiPolygon || geom instanceof com.vividsolutions.jts.geom.Point point)) {
            return;
        }

        try {
            BufferedImage iconImage = getIconWithTextImage(name, 20, icon, 50);

            // Get render Point
            double centerX = geom.getCentroid().getX();
            double centerY = geom.getCentroid().getY();

            int[] renderPoint = {
                    (int) ((centerX - offsetX) / meterPerPixel),
                    height - (int) ((centerY - offsetY) / meterPerPixel)
            };

            Graphics2D g = getGraphicForZ(99999999);
            g.drawImage(iconImage, renderPoint[0] - iconImage.getWidth() / 2 - 5, renderPoint[1] - iconImage.getHeight() / 2 - 5, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void drawBridge(int z, Geometry geom, Color color, int strokeWidth, Color secondaryColor, StreetCategory streetCategory) {
        strokeWidth = (int)(5 / meterPerPixel);
        if (streetCategory != null) {
            strokeWidth = scaleLineWidth(streetCategory.getWidth()) + (int)(5 / meterPerPixel);
        }
        if (geom instanceof com.vividsolutions.jts.geom.Polygon polygon) {
            drawPolygon(z, polygon, color, new BasicStroke(strokeWidth), secondaryColor);
            return;
        }
        // Todo - draw lines, but what about the polygons it looks ugly if doing both.
        //drawGeometryBasedOnType(z, geom, color, new BasicStroke(strokeWidth), secondaryColor);
    }

    private int scaleLineWidth(double lineWidth) {
        int width = (int) Math.floor((lineWidth / meterPerPixel) + 1);
        return width;
    }

    private void drawStreet(Geometry geom, StreetCategory streetCategory, String name) {
        int width = scaleLineWidth(streetCategory.getWidth());
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
            drawMultiPolygon(z, multiPolygon, primaryColor, stroke, secondaryColor);
        } else {
            System.out.println("Unknown geometry type: " + geom.getClass());
        }
    }

    private void drawMultiPolygon(int z, com.vividsolutions.jts.geom.MultiPolygon multiPolygon, Color color, BasicStroke stroke, Color secondaryColor) {

        String importId = DeproDBHelper.getImportId(currentDrawId);
        if (importId != null) {
            System.out.println("Import ID: " + importId + " Current Draw ID: " + currentDrawId);
            Geometry outerGeometry = buildOuterGeometry(importId);
            if (outerGeometry != null) {
                Geometry innerGeometry = buildInnerGeometry(importId);
                if (innerGeometry != null) {
                    // Subtract inner geometry from outer geometry
                    Geometry difference = outerGeometry.difference(innerGeometry);
                    drawGeometryBasedOnType(z, difference, color, stroke, secondaryColor);
                } else {
                    drawGeometryBasedOnType(z, outerGeometry, new Color(0, 0, 0, 0), stroke, secondaryColor);
                }
                System.out.println("Rebuilt geometry for d_id: " + currentDrawId);
                return;
            }
        }

        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            drawPolygon(z, (com.vividsolutions.jts.geom.Polygon) multiPolygon.getGeometryN(i), color, stroke, secondaryColor);
        }
    }

    private Geometry buildOuterGeometry(String importId) {
        ResultSet outerComponents = DeproDBHelper.getRelationComponents("outer", importId);

        try {
            WKBReader wkbReader = new WKBReader();
            Polygonizer polygonizer = new Polygonizer();
            UTMProjection utmProjection = new UTMProjection();

            while(outerComponents.next()) {
                Geometry geom = wkbReader.read(outerComponents.getBytes(3));
                geom = utmProjection.projectGeometry(geom);

                if (geom instanceof GeometryCollection) {
                    for (int i = 0; i < geom.getNumGeometries(); i++) {
                        polygonizer.add(geom.getGeometryN(i));
                    }
                } else {
                    polygonizer.add(geom);
                }
            }
            outerComponents.close();

            @SuppressWarnings("unchecked")
            Collection<com.vividsolutions.jts.geom.Polygon> polygons = polygonizer.getPolygons();
            if (polygons.isEmpty()) {
                return null;
            }

            GeometryFactory geometryFactory = new GeometryFactory();
            Geometry result = geometryFactory.buildGeometry(polygons).union();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Geometry buildInnerGeometry(String importId) {
        ResultSet innerComponents = DeproDBHelper.getRelationComponents("inner", importId);
        Geometry unionGeometry = null;

        try {
            WKBReader wkbReader = new WKBReader();
            UTMProjection utmProjection = new UTMProjection();

            while(innerComponents.next()) {
                Geometry geom = wkbReader.read(innerComponents.getBytes(3));
                geom = utmProjection.projectGeometry(geom);

                if (unionGeometry == null) {
                    unionGeometry = geom;
                } else {
                    unionGeometry = unionGeometry.union(geom);
                }
            }
            innerComponents.close();

            return unionGeometry;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
        return getTextShape(g, text, fontSize);
    }

    private Shape getTextShape(Graphics2D g, String text, int fontSize) {
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
        drawTextShape(g, textShape, color, angle);
    }

    private void drawTextShape(Graphics2D g, Shape textShape, Color color, double angle) {
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

    private BufferedImage getScaledImage(BufferedImage img, int targetWidth) {
        double scale = targetWidth / (double) img.getWidth();
        Image tempImg = img.getScaledInstance(targetWidth, (int) (img.getHeight() * scale), Image.SCALE_SMOOTH);
        BufferedImage scaledImage = new BufferedImage(tempImg.getWidth(null), tempImg.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D scaledGraphic = scaledImage.createGraphics();
        scaledGraphic.drawImage(tempImg, 0, 0, null);
        scaledGraphic.dispose();
        return scaledImage;
    }

    private BufferedImage getIconWithTextImage(String text, int fontSize, String icon, int iconTargetWidth) throws IOException {
        // get shape on random layer to have bounds
        Shape textShape = getTextShape(-1, text, fontSize);
        Rectangle textBounds = textShape.getBounds();

        // Load and scale icon
        BufferedImage scaledImage = getScaledImage(ImageIO.read(new File("icons" + File.separator + icon + ".png")), iconTargetWidth);

        // Assume text is placed below the icon
        int totalWidth = Math.max(scaledImage.getWidth(), textBounds.width);
        int totalHeight = (int) (scaledImage.getHeight() + textBounds.height);

        // Create combined image
        BufferedImage combinedImage = new BufferedImage(totalWidth + 30, totalHeight + 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gCombined = combinedImage.createGraphics();
        gCombined.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw text on correct graphic and transform
        textShape = getTextShape(gCombined, text, fontSize);
        AffineTransform transform = AffineTransform.getTranslateInstance(
                (totalWidth - textBounds.width) / 2 + 5,
                scaledImage.getHeight() + textBounds.getHeight() + 7
        );
        textShape = transform.createTransformedShape(textShape);

        // Get bounding shape centered at the top of totalHeight
        RoundRectangle2D imageRect = new RoundRectangle2D.Double((totalWidth - scaledImage.getWidth()) / 2 + 5, 5, scaledImage.getWidth(), scaledImage.getHeight(), 25, 25);
        Area combinedArea = new Area(imageRect);
        Rectangle2D tempRect = textShape.getBounds2D();
        RoundRectangle2D textRect = new RoundRectangle2D.Double(tempRect.getX() - 2, tempRect.getY() - 2, tempRect.getWidth() + 4, tempRect.getHeight() + 4, 10, 10);
        combinedArea.add(new Area(textRect));

        // Make an outline
        BasicStroke stroke = new BasicStroke(10.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Shape outline = stroke.createStrokedShape(combinedArea);

        // Draw background
        gCombined.setColor(new Color(0, 86, 159)); // green
        gCombined.fill(outline);

        // clear area of combinedArea
        gCombined.setComposite(AlphaComposite.Clear);
        gCombined.fill(combinedArea);
        gCombined.setComposite(AlphaComposite.SrcOver);
        gCombined.setColor(new Color(0, 114, 210, 130));
        gCombined.fill(combinedArea);

        // Draw icon
        gCombined.drawImage(scaledImage, (totalWidth - scaledImage.getWidth()) / 2 + 5, 5, null);

        // Draw text shape
        drawTextShape(gCombined, textShape, Color.WHITE, 0);

        gCombined.dispose();

        return combinedImage;
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
