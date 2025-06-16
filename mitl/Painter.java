package mitl;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import fu.keys.LSIClassCentreDB;
import mitl.projection.UTMProjection;

import javax.imageio.ImageIO;
import javax.xml.transform.Result;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class Painter {
    class Label {
        private final int priority;
        private String text;
        private double x;
        private double y;
        private int fontSize;
        private final String icon;

        public Label(int priority, String text, double x, double y, int fontSize, String icon) {
            this.priority = priority;
            this.text = text;
            this.x = x;
            this.y = y;
            this.fontSize = fontSize;
            this.icon = icon;
        }
    }

    private enum TextIconSide {
        BOTTOM, RIGHT, TOP, LEFT
    }

    private HashMap<Integer, BufferedImage> zImages = new HashMap<>();
    private HashMap<Integer, Graphics2D> gLayers = new HashMap<>();

    private PriorityQueue<Label> labelsToDraw = new PriorityQueue<>(Comparator.comparingInt(l -> -l.priority));
    private Set<String> drawnMultis = new HashSet<>();

    private final int width;
    private final int height;
    private final double meterPerPixel;
    private final double offsetX;
    private final double offsetY;
    private int currentDrawId = 0;
    private int currentLsiClass = 0;

    public static enum StreetCategory {
        AUTOBAHN(ColorPalette.ROAD_PRIMARY, 12),
        KRAFTFAHRSTRASSE(ColorPalette.ROAD_PRIMARY, 8),
        BUNDESSTRASSE(ColorPalette.ROAD_PRIMARY, 6),
        STANDARD_STRASSE(ColorPalette.ROAD_PRIMARY, 3),
        FELD_WALD_WEG(ColorPalette.ROAD_PRIMARY, 3),
        AUFFAHRT(ColorPalette.ROAD_PRIMARY, 3),
        ZUFAHRTPARKPLATZWEG(ColorPalette.ROAD_PRIMARY, 2);

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
        gLayer0.setColor(ColorPalette.MAP_BACKGROUND);
        gLayer0.fillRect(0, 0, width, height);
        gLayers.put(0, gLayer0);
        zImages.put(0, layer0);
    }

    public void saveImage(String filename) {
        drawLabels();
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
        this.currentLsiClass = lsiClass;
        if (name.length() <= 3) {
            name = "";
        }
        if (paintType == null) {
            paintType = LSIMapper.lsiCodeToPaintType(lsiClass);
        }
        LSIMapper.PaintType paintType2 = LSIMapper.lsiCodeToPaintType(lsiClass2);
        LSIMapper.PaintType paintType3 = LSIMapper.lsiCodeToPaintType(lsiClass3);

        if (paintType == null) {
            return;
        }
        int z = paintType.getZ();
        if (paintType == LSIMapper.PaintType.TrainStation && name.isEmpty() || paintType == LSIMapper.PaintType.Comercial) {
            // If the paint type is TrainStation and no name is given, use the LSI class name
            System.out.println("Drawing on layer " + z + " with name: " + LSIClassCentreDB.className(lsiClass));
        }

        switch (paintType) {
            case Autobahn -> drawStreet(geom, StreetCategory.AUTOBAHN, name);
            case Bundesstrasse -> drawStreet(geom, StreetCategory.BUNDESSTRASSE, name);
            case Kraftfahrstrasse -> drawStreet(geom, StreetCategory.KRAFTFAHRSTRASSE, name);
            case StandardStrasse -> drawStreet(geom, StreetCategory.STANDARD_STRASSE, name);
            case FeldWaldWeg -> drawStreet(geom, StreetCategory.FELD_WALD_WEG, name);
            case Auffahrt -> drawStreet(geom, StreetCategory.AUFFAHRT, name);
            case AdditionalSmallRoads -> drawStreet(geom, StreetCategory.ZUFAHRTPARKPLATZWEG, name);
            case Rail -> {
                float scaleFactor = (float) width / 1024f;
                float[] dashPattern = new float[] {8.0f, 4.0f};
                for (int i = 0; i < dashPattern.length; i++) {
                    dashPattern[i] *= scaleFactor;
                }
                BasicStroke scaledStroke = new BasicStroke(
                        1,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_BEVEL,
                        0f,
                        dashPattern,
                        2.0f
                );
                drawGeometryBasedOnType(z, geom, ColorPalette.RAIL_TRACK_PRIMARY, scaledStroke, null);
            }
            case GeneralGreen, Naherholungsgebiet -> drawGeometryBasedOnType(z, geom, ColorPalette.GENERAL_GREEN, 5, null);
            case Forest -> drawGeometryBasedOnType(z, geom, ColorPalette.FOREST_GREEN, 5, null);
            case Sportplatz, Fussballplatz -> drawSpecialArea(z, geom, ColorPalette.SPORTS_GREEN_PRIMARY, 5, ColorPalette.SPORTS_GREEN_SECONDARY, "sports", name, 15);
            case Playground -> drawGeometryBasedOnType(z, geom, ColorPalette.PLAYGROUND_GREEN_PRIMARY, 5, ColorPalette.PLAYGROUND_GREEN_SECONDARY);
            case FootCyclePath -> drawGeometryBasedOnType(z, geom, ColorPalette.ROAD_PRIMARY, 1, null);
            case PedestrianZone -> drawSpecialArea(z, geom, ColorPalette.PLAZA_PRIMARY, 1, ColorPalette.PLAZA_SECONDARY, "pedestrianzone", name, 20);
            case Overnight -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "hotel", name, 15);
            case Building, Unspecified0Building -> drawGeometryBasedOnType(z, geom, ColorPalette.BUILDING_PRIMARY, 3, ColorPalette.BUILDING_SECONDARY);
            case Water -> drawGeometryBasedOnType(z, geom, ColorPalette.WATER_PRIMARY, 3, ColorPalette.WATER_SECONDARY);
            case Bridge -> {
                drawBridge(z, geom, ColorPalette.BRIDGE_PRIMARY, 3, ColorPalette.BRIDGE_SECONDARY, paintTypeToStreetCategory(paintType2));

                // If there is a second paint type, draw it as well, for example unchanged streets
                if (paintType2 != null) {
                    paintLSIClass(d_id, lsiClass2, 0, 0, geom, name, paintType2);
                }
            }
            case Religious -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "religious", name, 25);
            case Cemetery -> drawSpecialArea(z, geom, ColorPalette.CEMETERY_GREEN_PRIMARY, 3, ColorPalette.CEMETERY_GREEN_SECONDARY, "graveyard", name, 25);
            case MedicalArea -> drawSpecialArea(z, geom, ColorPalette.YELLOW_AREA_PRIMARY, 3, ColorPalette.YELLOW_AREA_SECONDARY, "hospital2", name, 25);
            case EducationArea -> drawSpecialArea(z, geom, ColorPalette.YELLOW_AREA_PRIMARY, 3, ColorPalette.YELLOW_AREA_SECONDARY, "school", name, 20);
            case UniversityArea -> drawSpecialArea(z, geom, ColorPalette.YELLOW_AREA_PRIMARY, 3, ColorPalette.YELLOW_AREA_SECONDARY, "university", name, 25);
            case FireDeparmentArea -> drawSpecialArea(z, geom, ColorPalette.RED_AREA_PRIMARY, 3, ColorPalette.RED_AREA_SECONDARY, "firedepartment", name, 25);
            case PoliceArea -> drawSpecialArea(z, geom, ColorPalette.RED_AREA_PRIMARY, 3, ColorPalette.RED_AREA_SECONDARY, "police", name, 25);
            case Pharmacy -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "pharmacy", name, 25);
            case ATM -> drawSpecialArea(z, geom, ColorPalette.YELLOW_AREA_PRIMARY, 3, ColorPalette.YELLOW_AREA_SECONDARY, "cashpoint", "", 5);
            case FinanceBuilding -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "bank", name, 25);
            case Theatre, Cinema, ConcertHall, CommunityLife -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "social", name, 20);
            case Museum -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "museum", name, 20);
            case AnimalInstitutions -> drawSpecialArea(z, geom, ColorPalette.TRANSPARENT, 20, ColorPalette.SPECIAL_BUILDING_PRIMARY, "zoo2", name, 20);
            case KindergartenArea -> drawSpecialArea(z, geom, ColorPalette.YELLOW_AREA_PRIMARY, 3, ColorPalette.YELLOW_AREA_SECONDARY, "kindergarten", name, 15);
            case ThemeParkArea -> drawSpecialArea(z, geom, ColorPalette.YELLOW_AREA_PRIMARY, 3, ColorPalette.YELLOW_AREA_SECONDARY, "social", name, 20);
            case Court -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "court", name, 20);
            case CityHall -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "tower", name, 20);
            case Restaurants -> {
                drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "restaurant", name, 5);
                System.out.println("Gastronomy: " + name + ", lsiclass: " + LSIClassCentreDB.className(lsiClass));
            }
            case Comercial -> drawGeometryBasedOnType(z, geom, new Color(0, 0, 255, 150), 3, ColorPalette.SPECIAL_BUILDING_SECONDARY);
            case SwimmingAll -> {
                System.out.println("SwimmingAll: " + name + ", lsiclass: " + LSIClassCentreDB.className(lsiClass));
                drawGeometryBasedOnType(z, geom, ColorPalette.SWIMMING_BLUE_PRIMARY, 3, ColorPalette.SWIMMING_BLUE_SECONDARY);
            }
            case RailPlatform, RoofWorkaround -> drawGeometryBasedOnType(z, geom, ColorPalette.RAIL_PLATFORM_PRIMARY, 1, ColorPalette.RAIL_PLATFORM_SECONDARY);
            case Sand -> drawGeometryBasedOnType(z, geom, ColorPalette.SAND_PRIMARY, 3, ColorPalette.SAND_SECONDARY);
            case Tower -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "tower", name, 5);
            case HistoricOthersArea -> drawSpecialArea(z, geom, ColorPalette.TRANSPARENT, 3, ColorPalette.TRANSPARENT, "tower", name, 15);
            case Hairdresser -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "hairdresser", name, 5);
            case ClothingAndShoeShops -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "shop_shoes", name, 5);
            case GenericShop -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "shop", name, 5);
            case Bookstore -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "library", name, 5);
            case BicycleStore -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "bike", name, 5);
            case Gallery -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "museum", name, 15);
            case Florist -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "shop_flower", name, 5);
            case GiftShop -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "present", name, 10);
            case Bakery -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "baker", name, 3);
            case Butcher -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "butcher", name, 3);
            case TrainStation -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "trainstation", name, 25);
            case Post -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "post", name, 25);
            case Toilet -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "toilet", "", 5);
            case Cardealerships -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "cardealer", name, 5);
            case CarWash -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "car", name, 5);
            case GasStation -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "gasstation", name, 15);
            case TouristInformation -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "touristinfo", name, 25);
            case CommercialArea -> drawGeometryBasedOnType(z, geom, ColorPalette.COMMERCIAL_AREA_PRIMARY, 3, ColorPalette.COMMERCIAL_AREA_SECONDARY);
            case PublicParking -> drawSpecialArea(z, geom, ColorPalette.PLAZA_PRIMARY, 3, ColorPalette.PLAZA_SECONDARY, "parkinglot", "", 15);
            case CarParking -> drawSpecialArea(z, geom, ColorPalette.BUILDING_PRIMARY, 3, ColorPalette.BUILDING_SECONDARY, "parking_house", "", 15);
            case TaxiRank -> drawSpecialArea(z, geom, ColorPalette.PLAZA_PRIMARY, 3, ColorPalette.PLAZA_SECONDARY, "taxi", "", 15);
            case GastronomyArea -> drawGeometryBasedOnType(z, geom, ColorPalette.GASTRONOMY_AREA_PRIMARY, 3, ColorPalette.GASTRONOMY_AREA_SECONDARY);
            case Markets -> drawSpecialArea(z, geom, ColorPalette.MARKET_PRIMARY, 3, ColorPalette.MARKET_SECONDARY, "fontain", name, 15);
            case Bar -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "drink", name, 5);
            case Cafe -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "coffee", name, 5);
            case IceCreamShop -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "icecream", name, 5);
            case CommerceBuildings -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "shop", name, 5);
            case Craftmanship -> drawSpecialArea(z, geom, ColorPalette.SPECIAL_BUILDING_PRIMARY, 3, ColorPalette.SPECIAL_BUILDING_SECONDARY, "craft", name, 5);
            //default -> System.out.println("Unhandled LSI code: " + lsiClass);
        }
    }

    private Set<String> getLabelPermutations(String labelText) {
        // Get some text line permutations:
        // 1. Keep the label text
        // 2. Split on space but recombine the split results until each part is less than 15 characters and then place a "\n" between them

        Set<String> permutations = new HashSet<>();

        if (labelText.length() >= 15) {
            String[] parts = labelText.split(" ");
            StringBuilder currentLine = new StringBuilder();
            StringBuilder currentSection = new StringBuilder();
            for (String part : parts) {
                // if part is punctuation only, skip it
                if (part.matches("\\p{Punct}+")) {
                    continue;
                }

                if (currentSection.length() + part.length() + 1 <= 20 || currentSection.length() == 0) {
                    if (currentSection.length() > 0) {
                        currentSection.append(" ");
                    }
                    currentSection.append(part);
                } else {
                    currentLine.append(currentSection.toString());
                    currentLine.append("\n");
                    currentSection.setLength(0); // Clear the current section
                    currentSection.append(part); // Start a new section with the current part
                }
            }
            if (currentLine.length() > 0) {
                if (currentSection.length() > 0) {
                    // if last char is not newline, append it
                    if (currentLine.charAt(currentLine.length() - 1) != '\n') {
                        currentLine.append("\n");
                    }
                    currentLine.append(currentSection.toString());
                }

                permutations.add(currentLine.toString());
            }
        }
        if (labelText.length() <= 20 || permutations.isEmpty()) {
            permutations.add(labelText);
        }

        return permutations;
    }

    private void drawLabels() {
        int priorityCutoff = (int) (meterPerPixel * width / 100);
        System.out.println("Priority cutoff for labels: " + priorityCutoff);
        Graphics2D g = getGraphicForZ(100000000);

        while (!labelsToDraw.isEmpty() && labelsToDraw.peek().priority >= priorityCutoff) {
            Label label = labelsToDraw.poll();
            if (label.text.contains("_")) {
                label.text = "";
            }
            int importance = label.priority / 5 - 1;
            label.fontSize += importance;
            int iconTargetWidth = (int)((0.015 * width) + (1 + importance) * (width / 1024.0));

            Set<String> labelPermutations = getLabelPermutations(label.text);

            float lowestOverlap = 1.0f;
            String bestPermutation = "";
            BufferedImage bestLabelImage = null;
            int[] bestRenderPoint = new int[2];
            TextIconSide bestTextIconSide = TextIconSide.BOTTOM;

            // For each texticonside
            for (TextIconSide textIconSide : TextIconSide.values()) {
                // For all label Permutations test their overlap ratio
                for (String permutation : labelPermutations) {
                    BufferedImage labelImage;

                    try {
                        labelImage = getLabel(permutation, label.fontSize, label.icon, iconTargetWidth, textIconSide);
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }

                    int[] renderPoint = {
                            (int) ((label.x - offsetX) / meterPerPixel) - labelImage.getWidth() / 2,
                            height - (int) ((label.y - offsetY) / meterPerPixel) - labelImage.getHeight() / 2
                    };

                    float overlapRatio = getOverlapRatio(labelImage, renderPoint[0], renderPoint[1], zImages.get(100000000));

                    if (overlapRatio < lowestOverlap) {
                        lowestOverlap = overlapRatio;
                        bestPermutation = permutation;
                        bestLabelImage = labelImage;
                        bestRenderPoint[0] = renderPoint[0];
                        bestRenderPoint[1] = renderPoint[1];
                        bestTextIconSide = textIconSide;
                    }
                }
            }


            if (bestLabelImage == null) {
                continue; // No valid label found
            }

            System.out.println("Drawing label: " + bestPermutation + " at (" + bestRenderPoint[0] + ", " + bestRenderPoint[1] + ") with overlap: " + lowestOverlap);

            // Draw the best label image, if overlap is over 0.25 try to fall back to icon only with label text ""
            if (lowestOverlap < 0.25f) {
                g.drawImage(bestLabelImage, bestRenderPoint[0], bestRenderPoint[1], null);
            } else {
                // Fallback to icon only
                BufferedImage iconImage;
                try {
                    iconImage = getLabel("", label.fontSize, label.icon, iconTargetWidth, bestTextIconSide);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }

                bestRenderPoint[0] = (int) ((label.x - offsetX) / meterPerPixel) - iconImage.getWidth() / 2;
                bestRenderPoint[1] = height - (int) ((label.y - offsetY) / meterPerPixel) - iconImage.getHeight() / 2;

                if (getOverlapRatio(iconImage, bestRenderPoint[0], bestRenderPoint[1], zImages.get(100000000)) > 0.25f) {
                    System.out.println("Skipping icon for label: " + label.text + " due to high overlap in every draw version");
                    continue; // If the icon overlaps too much, skip drawing
                }

                g.drawImage(iconImage, bestRenderPoint[0], bestRenderPoint[1], null);
            }

            g = getGraphicForZ(100000000);
            // DEBUG - Draw a point at the center of the text
            g.setColor(Color.RED);
            g.fillOval(bestRenderPoint[0] + bestLabelImage.getWidth() / 2 - 2, bestRenderPoint[1] + bestLabelImage.getHeight() / 2 - 2, 4, 4);
        }
    }

    public float getOverlapRatio(BufferedImage label, int x, int y, BufferedImage mask) {
        int overlapPixels = 0;
        int labelPixels = 0;

        for (int j = 0; j < label.getHeight(); j++) {
            for (int i = 0; i < label.getWidth(); i++) {
                int labelAlpha = (label.getRGB(i, j) >> 24) & 0xff;
                if (labelAlpha != 0) {
                    labelPixels++;
                    int maskX = x + i;
                    int maskY = y + j;
                    // If label is out of bounds high ratio to not print
                    if (maskX < 0 || maskY < 0 || maskX >= mask.getWidth() || maskY >= mask.getHeight()) {
                        return 1.0f;
                    }

                    if (maskX >= 0 && maskY >= 0 && maskX < mask.getWidth() && maskY < mask.getHeight()) {
                        int maskAlpha = (mask.getRGB(maskX, maskY) >> 24) & 0xff;
                        if (maskAlpha != 0) {
                            overlapPixels++;
                        }
                    }
                }
            }
        }

        return labelPixels == 0 ? 0 : (float) overlapPixels / labelPixels;
    }


    private StreetCategory paintTypeToStreetCategory(LSIMapper.PaintType paintType) {
        if (paintType == null) {
            return null;
        }
        return switch (paintType) {
            case Autobahn -> StreetCategory.AUTOBAHN;
            case Bundesstrasse -> StreetCategory.BUNDESSTRASSE;
            case Kraftfahrstrasse -> StreetCategory.KRAFTFAHRSTRASSE;
            case StandardStrasse -> StreetCategory.STANDARD_STRASSE;
            case FeldWaldWeg -> StreetCategory.FELD_WALD_WEG;
            case Auffahrt -> StreetCategory.AUFFAHRT;
            case AdditionalSmallRoads -> StreetCategory.ZUFAHRTPARKPLATZWEG;
            default -> null;
        };
    }

    private void drawSpecialArea(int z, Geometry geom, Color color, int strokeWidth, Color secondaryColor, String icon, String name, int labelPriority) {
        drawGeometryBasedOnType(z, geom, color, new BasicStroke(strokeWidth), secondaryColor);

        // Filters out for example inner paths or the like
        if (!(geom instanceof com.vividsolutions.jts.geom.Polygon polygon || geom instanceof com.vividsolutions.jts.geom.MultiPolygon multiPolygon || geom instanceof com.vividsolutions.jts.geom.Point point)) {
            return;
        }

        if (name.contains("_")) {
            name = LSIClassCentreDB.className(currentLsiClass);
        }

        int fontSize = (int) Math.max(9, 9.0 * (width / 1024.0) * 0.8);

        labelsToDraw.add(new Label(labelPriority, name, geom.getCentroid().getX(), geom.getCentroid().getY(), fontSize, icon));
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
    }

    private int scaleLineWidth(double lineWidth) {
        int width = (int) Math.floor((lineWidth / meterPerPixel) + 1);
        return width;
    }

    private void drawStreet(Geometry geom, StreetCategory streetCategory, String name) {
        int lineWidth = scaleLineWidth(streetCategory.getWidth());
        Color color = streetCategory.getColor();

        drawGeometryBasedOnType(LSIMapper.PaintType.StandardStrasse.getZ(), geom, color, lineWidth, null);

        // Draw text segments
        if (name.contains("_")) {
            return;
        }

        int fontSize = (int) Math.max(10, 10.0 * (width / 1024.0) * 0.8);
        int fontWidth = (int) (fontSize * 0.6); // Approximate width of a char

        if (geom instanceof LineString lineString) {
            int distSinceLastText = Integer.MAX_VALUE;
            for (int i = 0; i < lineString.getNumPoints() - 1; i++) {
                Coordinate start = lineString.getCoordinateN(i);
                Coordinate end = lineString.getCoordinateN(i + 1);

                double StartX = start.x;
                double StartY = start.y;
                double EndX = end.x;
                double EndY = end.y;

                double length = Math.sqrt(Math.pow(EndX - StartX, 2) + Math.pow(EndY - StartY, 2)) / meterPerPixel;
                if (length < name.length() * fontWidth || distSinceLastText < name.length() * fontWidth * 1.5) {
                    distSinceLastText += length;
                    continue; // Skip if the length is too short
                }

                double angle = -Math.toDegrees(Math.atan2(EndY - StartY, EndX - StartX));

                double midX = (StartX + EndX) / 2;
                double midY = (StartY + EndY) / 2;

                if (streetCategory == StreetCategory.AUTOBAHN) {
                    //TODO - use Autobahn Icon
                } else if (streetCategory == StreetCategory.BUNDESSTRASSE) {
                    //TODO - use Bundestrasse Icon
                }
                
                Shape textShape = getTextShape(9999998, name, fontSize);
                Shape centeredTextShape = centerTextShape(textShape, midX, midY);

                // Dont draw if text goes over borders
                Rectangle2D bounds = centeredTextShape.getBounds2D();
                if (bounds.getX() < 0 || bounds.getY() < 0 || bounds.getX() + bounds.getWidth() > width || bounds.getY() + bounds.getHeight() > height) {
                    distSinceLastText = 0; // Reset distance if text goes over borders
                    continue;
                }

                //System.out.println("Drawing street name: " + name + " at angle: " + angle);
                drawTextShape(9999998, centeredTextShape, Color.WHITE, angle, Color.BLACK);

                distSinceLastText = 0; // Reset distance after drawing text
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
            if (drawnMultis.contains(importId)) {
                System.out.println("Already drawn MultiPolygon for import ID: " + importId);
                return; // Skip if already drawn
            } else {
                drawnMultis.add(importId);
            }
            System.out.println("Import ID: " + importId + " Current Draw ID: " + currentDrawId);
            Geometry outerGeometry = buildOuterGeometry(importId);
            if (outerGeometry != null) {
                Geometry innerGeometry = buildInnerGeometry(importId);
                if (innerGeometry != null) {
                    // Subtract inner geometry from outer geometry
                    Geometry difference = outerGeometry.difference(innerGeometry);
                    drawGeometryBasedOnType(z, difference, color, stroke, secondaryColor);
                } else {
                    drawGeometryBasedOnType(z, outerGeometry, ColorPalette.TRANSPARENT, stroke, secondaryColor);
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
        QueryData outerComponents = DeproDBHelper.getRelationComponents("outer", importId);

        try {
            WKBReader wkbReader = new WKBReader();
            Polygonizer polygonizer = new Polygonizer();
            UTMProjection utmProjection = new UTMProjection();

            while(outerComponents.resultSet.next()) {
                Geometry geom = wkbReader.read(outerComponents.resultSet.getBytes(3));
                geom = utmProjection.projectGeometry(geom);

                if (geom instanceof GeometryCollection) {
                    for (int i = 0; i < geom.getNumGeometries(); i++) {
                        polygonizer.add(geom.getGeometryN(i));
                    }
                } else {
                    polygonizer.add(geom);
                }
            }

            @SuppressWarnings("unchecked")
            Collection<com.vividsolutions.jts.geom.Polygon> polygons = polygonizer.getPolygons();
            if (polygons.isEmpty()) {
                return null;
            }

            GeometryFactory geometryFactory = new GeometryFactory();
            Geometry result = geometryFactory.buildGeometry(polygons).union();
            outerComponents.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        outerComponents.close();
        return null;
    }

    private Geometry buildInnerGeometry(String importId) {
        QueryData innerComponents = DeproDBHelper.getRelationComponents("inner", importId);
        WKBReader wkbReader = new WKBReader();
        UTMProjection utmProjection = new UTMProjection();
        GeometryFactory geometryFactory = new GeometryFactory();

        List<Geometry> lineStrings = new ArrayList<>();
        List<Geometry> otherGeoms = new ArrayList<>();

        try {
            while (innerComponents.resultSet.next()) {
                Geometry geom = wkbReader.read(innerComponents.resultSet.getBytes(3));
                geom = utmProjection.projectGeometry(geom);

                if (geom instanceof LineString) {
                    // Just keep line strings for now
                    lineStrings.add(geom);
                } else {
                    // Keep other geometries as is
                    otherGeoms.add(geom);
                }
            }

            // Use Polygonizer to form polygons from lineStrings
            Polygonizer polygonizer = new Polygonizer();
            polygonizer.add(lineStrings);

            // Get the polygons formed
            @SuppressWarnings("unchecked")
            Collection<com.vividsolutions.jts.geom.Polygon> polygons = polygonizer.getPolygons();

            // Combine polygons and other geometries
            List<Geometry> allGeoms = new ArrayList<>();
            allGeoms.addAll(polygons);
            allGeoms.addAll(otherGeoms);

            // Union all into one geometry
            Geometry unionGeometry = null;
            for (Geometry g : allGeoms) {
                if (unionGeometry == null) {
                    unionGeometry = g;
                } else {
                    unionGeometry = unionGeometry.union(g);
                }
            }

            innerComponents.close();
            return unionGeometry;

        } catch (Exception e) {
            e.printStackTrace();
        }

        innerComponents.close();
        return null;
    }

    private Geometry normalizeToPolygonIfClosed(Geometry geometry, GeometryFactory factory) {
        if (geometry instanceof LineString) {
            LineString line = (LineString) geometry;

            if (line.isClosed() && line.getNumPoints() >= 4) {
                try {
                    return factory.createPolygon(line.getCoordinateSequence());
                } catch (IllegalArgumentException e) {
                    // Could not convert; fallback to original
                    System.err.println("Could not convert LineString to Polygon: " + e.getMessage());
                    return line;
                }
            }
        }
        return geometry;
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
        String[] lines = text.split("\n");
        Font font = new Font("Arial", Font.PLAIN, fontSize);
        FontRenderContext frc = g.getFontRenderContext();

        // Get Text shape for each line and combine them centered
        Area combinedArea = null;
        for (String line : lines) {
            GlyphVector gv = font.createGlyphVector(frc, line);
            Shape textShape = gv.getOutline();
            if (combinedArea == null) {
                combinedArea = new Area(textShape);
            } else {
                // Get current bounds and center the new text shape
                Rectangle2D bounds = combinedArea.getBounds2D();
                Shape centeredTextShape = AffineTransform.getTranslateInstance(
                        bounds.getCenterX() - textShape.getBounds2D().getCenterX(),
                        bounds.getMaxY() + fontSize
                ).createTransformedShape(textShape);
                combinedArea.add(new Area(centeredTextShape));
            }
        }

        /*Font font = new Font("Arial", Font.PLAIN, fontSize);
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, text);
        Shape textShape = gv.getOutline();*/
        return combinedArea;
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

    private void drawTextShape(int z, Shape textShape, Color color, double angle, Color outlineColor) {
        Graphics2D g = getGraphicForZ(z);
        drawTextShape(g, textShape, color, angle, outlineColor);
    }

    private void drawTextShape(Graphics2D g, Shape textShape, Color color, double angle, Color outlineColor) {
        if (angle < 0) {
            angle += 180;
        }
        if (angle > 90) {
            angle -= 180;
        }

        // Rotate the graphics context
        g.rotate(Math.toRadians(angle), textShape.getBounds2D().getCenterX(), textShape.getBounds2D().getCenterY());
        if (outlineColor != null) {
            g.setColor(outlineColor);
            g.setStroke(new BasicStroke(2f));
            g.draw(textShape);
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(1));
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

    private BufferedImage getLabel(String text, int fontSize, String icon, int iconTargetWidth, TextIconSide side) throws IOException {
        // get shape on random layer to have bounds
        Shape textShape = getTextShape(-1, text, fontSize);
        Rectangle textBounds = textShape.getBounds();

        // Load and scale icon
        BufferedImage scaledImage = getScaledImage(ImageIO.read(new File("icons" + File.separator + icon + ".png")), iconTargetWidth);


        int totalWidth = 0;
        int totalHeight = 0;
        // Assume text is placed below the icon
        if (side == TextIconSide.LEFT || side == TextIconSide.RIGHT) {
            totalWidth = (int) (scaledImage.getWidth() + textBounds.width);
            totalHeight = Math.max(scaledImage.getHeight(), textBounds.height);
        } else {
            totalWidth = Math.max(scaledImage.getWidth(), textBounds.width);
            totalHeight = (int) (scaledImage.getHeight() + textBounds.height);
        }

        // Create combined image
        BufferedImage combinedImage = new BufferedImage(totalWidth + 500, totalHeight + 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gCombined = combinedImage.createGraphics();
        gCombined.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw text on correct graphic and transform
        textShape = getTextShape(gCombined, text, fontSize);
        // place centered
        Rectangle2D bounds = textShape.getBounds2D();

        int xOffset = 0;
        int yOffset = 0;

        switch (side) {
            case LEFT -> {
                xOffset = -(int) (bounds.getWidth() / 2 + scaledImage.getWidth() / 2) - 2;
            }
            case RIGHT -> {
                xOffset = (int) (bounds.getWidth() / 2 + scaledImage.getWidth() / 2) + 2;
            }
            case TOP -> {
                yOffset = -(int) (bounds.getHeight() / 2 + scaledImage.getHeight() / 2) - 2;
            }
            case BOTTOM -> {
                yOffset = (int) (bounds.getHeight() / 2 + scaledImage.getHeight() / 2) + 2;
            }
        }

        AffineTransform transform = AffineTransform.getTranslateInstance(
                (-(bounds.getX()) + (totalWidth + 500) / 2.0 - bounds.getWidth() / 2) + xOffset,
                (-(bounds.getY()) + (totalHeight + 500) / 2.0 - bounds.getHeight() / 2) + yOffset
        );
        textShape = transform.createTransformedShape(textShape);


        // Get bounding shape centered at the top of totalHeight
        RoundRectangle2D imageRect = new RoundRectangle2D.Double((totalWidth + 500) / 2.0 - scaledImage.getWidth() / 2.0, (totalHeight + 500) / 2.0 - scaledImage.getHeight() / 2.0, scaledImage.getWidth(), scaledImage.getHeight(), 25, 25);
        Area combinedArea = new Area(imageRect);
        Rectangle2D tempRect = textShape.getBounds2D();
        RoundRectangle2D textRect = new RoundRectangle2D.Double(tempRect.getX() - 2, tempRect.getY() - 2, tempRect.getWidth() + 4, tempRect.getHeight() + 4, 10, 10);
        if (!text.isEmpty()) {
            combinedArea.add(new Area(textRect));

            // Make an outline
            BasicStroke stroke = new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
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
        }

        // Draw icon
        gCombined.drawImage(scaledImage, (int)((totalWidth + 500) / 2.0 - scaledImage.getWidth() / 2.0), (int) ((totalHeight + 500) / 2.0 - scaledImage.getHeight() / 2.0), null);

        // Draw text shape
        drawTextShape(gCombined, textShape, Color.WHITE, 0, null);
        // oval in middle of BufferedImage
        gCombined.setColor(Color.MAGENTA);
        gCombined.fillOval((totalWidth + 500) / 2, (totalHeight + 500) / 2, 4, 4);

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
