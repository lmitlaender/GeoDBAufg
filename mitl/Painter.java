package mitl;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;
import fu.keys.LSIClass;
import fu.keys.LSIClassCentreDB;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
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

    public void paintLSIClass(int lsiClass, Geometry geom) {
        List<Integer> railList = new ArrayList<>();
        railList.add(LSIClassCentreDB.lsiClass("MONORAIL"));
        railList.add(LSIClassCentreDB.lsiClass("TRAM_GLEISE"));
        railList.add(LSIClassCentreDB.lsiClass("GLEISKOERPER"));

        List<Integer> standardRoadList = new ArrayList<>();
        standardRoadList.add(LSIClassCentreDB.lsiClass("LANDSTRASSE"));
        standardRoadList.add(LSIClassCentreDB.lsiClass("INNERORTSTRASSE"));
        standardRoadList.add(LSIClassCentreDB.lsiClass("ERSCHLIESSUNGSWEG"));
        // add with lambda
        IntStream.of(LSIClassCentreDB.subClasses(standardRoadList.get(0)))
                .forEach(standardRoadList::add);

        List<Integer> streetJunctionList = new ArrayList<>();
        streetJunctionList.add(LSIClassCentreDB.lsiClass("KREUZUNGEN_KREISEL_AUFFAHRTEN"));
        IntStream.of(LSIClassCentreDB.subClasses(streetJunctionList.get(0)))
                .forEach(streetJunctionList::add);

        List<Integer> vegetationList = new ArrayList<>();
        vegetationList.add(LSIClassCentreDB.lsiClass("VEGETATION"));
        IntStream.of(LSIClassCentreDB.subClasses(vegetationList.get(0)))
                .forEach(x -> {
                    vegetationList.add(x);
                    IntStream.of(LSIClassCentreDB.subClasses(x))
                            .forEach(y -> {
                                vegetationList.add(y);
                                IntStream.of(LSIClassCentreDB.subClasses(y))
                                        .forEach(z -> vegetationList.add(z));
                            });
                });

        List<Integer> footCyclePathList = new ArrayList<>();
        footCyclePathList.add(LSIClassCentreDB.lsiClass("FAHRRAD_FUSS_WEGE_ALL"));
        IntStream.of(LSIClassCentreDB.subClasses(footCyclePathList.get(0)))
                .forEach(x -> {
                    footCyclePathList.add(x);
                    IntStream.of(LSIClassCentreDB.subClasses(x))
                            .forEach(y -> {
                                footCyclePathList.add(y);
                                IntStream.of(LSIClassCentreDB.subClasses(y))
                                        .forEach(z -> footCyclePathList.add(z));
                            });
                });


        List<Integer> uebernachtungenList = new ArrayList<>();
        uebernachtungenList.add(LSIClassCentreDB.lsiClass("UEBERNACHTUNGEN"));
        IntStream.of(LSIClassCentreDB.subClasses(uebernachtungenList.get(0)))
                .forEach(uebernachtungenList::add);

        List<Integer> buildingList = new ArrayList<>();
        buildingList.add(LSIClassCentreDB.lsiClass("BUILDING"));
        IntStream.of(LSIClassCentreDB.subClasses(buildingList.get(0)))
                .forEach(x -> {
                    buildingList.add(x);
                    IntStream.of(LSIClassCentreDB.subClasses(x))
                            .forEach(y -> {
                                buildingList.add(y);
                                IntStream.of(LSIClassCentreDB.subClasses(y))
                                        .forEach(z -> buildingList.add(z));
                            });
                });

        List<Integer> waterList = new ArrayList<>();
        waterList.add(LSIClassCentreDB.lsiClass("WATER"));
        IntStream.of(LSIClassCentreDB.subClasses(waterList.get(0)))
                .forEach(x -> {
                    waterList.add(x);
                    IntStream.of(LSIClassCentreDB.subClasses(x))
                            .forEach(y -> {
                                waterList.add(y);
                                IntStream.of(LSIClassCentreDB.subClasses(y))
                                        .forEach(z -> waterList.add(z));
                            });
                });


        // TODO: Map width to average width for type
        if (railList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.lightGray, 1);
        } else if (lsiClass == LSIClassCentreDB.lsiClass("AUTOBAHN")) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.GREEN, 5);
        } else if (lsiClass == LSIClassCentreDB.lsiClass("KRAFTFAHRSTRASSE")) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.MAGENTA, 3);
        } else if (standardRoadList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.DARK_GRAY, 3);
        } else if (lsiClass == LSIClassCentreDB.lsiClass("FELD_WALD_WEG")) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.RED, 3);
        } else if (streetJunctionList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.MAGENTA, 3);
        } else if (vegetationList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[1], geom, new Color(234, 255, 225), 5);
        } else if (uebernachtungenList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[4], geom, Color.CYAN, 3);
        } else if (buildingList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[4], geom, Color.MAGENTA, 3);
        } else if (waterList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[1], geom, Color.BLUE, 3);
        // TODO: footCyclePathList hat gerade auch z.B. "Plätze" drin wie den Grünen Markt
        } else if (footCyclePathList.contains(lsiClass)) {
            drawGeometryBasedOnType(gLayers[3], geom, Color.gray, 1);
        } else if (lsiClass == LSIClassCentreDB.lsiClass("BRIDGE")) {
            drawGeometryBasedOnType(gLayers[2], geom, Color.darkGray, 3);
        }

        /*if (geom instanceof com.vividsolutions.jts.geom.Polygon) {
            if (lsiClass == 20120000) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.GREEN);
            } else if (lsiClass == 20120100) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.YELLOW);
            } else if (lsiClass == 20120200) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.ORANGE);
            } else if (lsiClass == 20120300) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.RED);
            } else if (lsiClass == 20120400) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.MAGENTA);
            } else if (lsiClass == 20120500) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.CYAN);
            } else if (lsiClass == 20120600) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.BLUE);
            } else if (lsiClass == 20120700) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.PINK);
            } else if (lsiClass == 20120800) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.LIGHT_GRAY);
            } else if (lsiClass == 20120900) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.DARK_GRAY);
            } else if (lsiClass == 20121000) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.GRAY);
            } else if (lsiClass == 20121100) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.WHITE);
            } else if (lsiClass == 20121200) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.BLACK);
            } else if (lsiClass == 20121300) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.DARK_GRAY);
            } else if (lsiClass == 20121400) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.LIGHT_GRAY);
            } else if (lsiClass == 20121500) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.YELLOW);
            } else if (lsiClass == 20121600) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.ORANGE);
            } else if (lsiClass == 20121700) {
                drawPolygon(gLayers[5], (Polygon) geom, Color.RED);
            } else { // Default color for unknown classes
                System.out.println("Unknown LSI class: " + lsiClass);
                drawPolygon(gLayers[5], (Polygon) geom, Color.GRAY);
            }

        }
        else if (geom instanceof LineString ls) {
            drawLineString(gLayers[8], ls, Color.RED, 3);
        }
        else
            System.out.println("Don't know how to paint " + geom.getClass());*/
    }

    private void drawGeometryBasedOnType(Graphics2D g, Geometry geom, Color color, int strokeWidth) {
        drawGeometryBasedOnType(g, geom, color, new BasicStroke(strokeWidth));
    }

    private void drawGeometryBasedOnType(Graphics2D g, Geometry geom, Color color, BasicStroke stroke) {
        if (geom instanceof Polygon polygon) {
            drawPolygon(g, polygon, color, stroke);
        } else if (geom instanceof LineString lineString) {
            drawLineString(g, lineString, color, stroke);
        } else if (geom instanceof com.vividsolutions.jts.geom.Point point){
            drawPoint(g, point.getX(), point.getY(), color, (int) stroke.getLineWidth());
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPoint multiPoint) {
            for (Coordinate coordinate : multiPoint.getCoordinates()) {
                drawPoint(g, coordinate.x, coordinate.y, color, (int) stroke.getLineWidth());
            }
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiLineString multiLineString) {
            for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                drawLineString(g, (LineString) multiLineString.getGeometryN(i), color, stroke);
            }
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPolygon multiPolygon) {
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                drawPolygon(g, (Polygon) multiPolygon.getGeometryN(i), color, stroke);
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
        g.rotate(Math.toRadians(angle), renderPoint[0], renderPoint[1]);
        g.drawString(text, renderPoint[0], renderPoint[1]);
        g.rotate(-Math.toRadians(angle), renderPoint[0], renderPoint[1]);
    }

    private void drawPolygon(Graphics2D g, Polygon polygon, Color color) {
        drawPolygon(g, polygon, color, new BasicStroke(1));
    }

    private void drawPolygon(Graphics2D g, Polygon polygon, Color color, Stroke stroke) {
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

    private void drawLineString(Graphics2D g, LineString lineString, Color color, Stroke stroke) {
        g.setColor(color);
        g.setStroke(stroke);
        for (int i = 0; i < lineString.getNumPoints() - 1; ++i) {
            Coordinate start = lineString.getCoordinateN(i);
            Coordinate end = lineString.getCoordinateN(i + 1);
            g.drawLine((int) ((start.x - offsetX) / meterPerPixel), height - (int) ((start.y - offsetY) / meterPerPixel),
                    (int) ((end.x - offsetX) / meterPerPixel), height - (int) ((end.y - offsetY) / meterPerPixel));
        }
    }

    private void drawPoint(Graphics2D g, double pointX, double pointY, Color color, int size) {
        g.setColor(color);
        g.fillOval((int) ((pointX - offsetX) / meterPerPixel), height - (int) ((pointY - offsetY) / meterPerPixel), size, size);
    }
}
