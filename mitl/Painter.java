package mitl;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

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
        if (geom instanceof com.vividsolutions.jts.geom.Polygon) {
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
            System.out.println("Don't know how to paint " + geom.getClass());
    }

    private void drawPolygon(Graphics2D g, Polygon polygon, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1));

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
        // Draw outline
        g.setColor(Color.BLACK);
        g.draw(area);
    }

    private void drawLineString(Graphics2D g, LineString lineString, Color color, int width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        for (int i = 0; i < lineString.getNumPoints() - 1; ++i) {
            Coordinate start = lineString.getCoordinateN(i);
            Coordinate end = lineString.getCoordinateN(i + 1);
            g.drawLine((int) ((start.x - offsetX) / meterPerPixel), height - (int) ((start.y - offsetY) / meterPerPixel),
                    (int) ((end.x - offsetX) / meterPerPixel), height - (int) ((end.y - offsetY) / meterPerPixel));
        }
    }
}
