package mitl;

import fu.keys.LSIClassCentreDB;

import java.awt.*;
import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

// Maps LSI Codes to the function to use and metadata needed
public class LSIMapper {

    public static enum PaintType {
        Autobahn,
        Kraftfahrstrasse,
        StandardStrasse,
        FeldWaldWeg,
        Auffahrt,
        Rail,
        Vegetation,
        FootCyclePath,
        Overnight,
        Building,
        Water,
        Bridge
    }

    // Static map for LSI lists
    private static final Map<PaintType, List<Integer>> LSI_LISTS = new HashMap<>();
    private static boolean initialized = false;

    public static PaintType lsiCodeToPaintType(int code) {
        if (!initialized) {
            for (PaintType type : PaintType.values()) {
                LSI_LISTS.put(type, getLSICodeListForPaintType(type));
            }
            initialized = true;
        }

        for (Map.Entry<PaintType, List<Integer>> entry : LSI_LISTS.entrySet()) {
            if (entry.getValue().contains(code)) {
                return entry.getKey();
            }
        }

        return null;
    }

    private static List<Integer> getLSICodeListForPaintType(PaintType type) {
        switch (type) {
            case Autobahn:
                return getLSICodeList(LSIClassCentreDB.lsiClass("AUTOBAHN"), false);
            case Kraftfahrstrasse:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KRAFTFAHRSTRASSE"), false);
            case StandardStrasse:
                return getStandardRoadLSICodeList();
            case FeldWaldWeg:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FELD_WALD_WEG"), false);
            case Auffahrt:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KREUZUNGEN_KREISEL_AUFFAHRTEN"), true);
            case Rail:
                return getRailLSICodeList();
            case Vegetation:
                return getLSICodeList(LSIClassCentreDB.lsiClass("VEGETATION"), true);
            case FootCyclePath:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FAHRRAD_FUSS_WEGE_ALL"), true);
            case Overnight:
                return getLSICodeList(LSIClassCentreDB.lsiClass("UEBERNACHTUNGEN"), true);
            case Building:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BUILDING"), true);
            case Water:
                return getLSICodeList(LSIClassCentreDB.lsiClass("WATER"), true);
            case Bridge:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BRIDGE"), true);
            default:
                throw new IllegalArgumentException("Unknown PaintType: " + type);
        }
    }

    public static List<Integer> getLSICodeList(int code, boolean addAllSubClasses) {
        List<Integer> lsiCodes = new java.util.ArrayList<>();
        lsiCodes.add(code);
        if (addAllSubClasses)
            appendAllSubLSICodes(lsiCodes, code);
        return lsiCodes;
    }

    public static void appendAllSubLSICodes(List<Integer> lsiCodes, int code) {
        IntStream.of(LSIClassCentreDB.subClasses(code))
                .forEach(x -> {
                    if (!lsiCodes.contains(x)) {
                        lsiCodes.add(x);
                        appendAllSubLSICodes(lsiCodes, x);
                    }
                });
    }

    public static List<Integer> getStandardRoadLSICodeList() {
        List<Integer> standardRoadList = new ArrayList<>();
        standardRoadList.add(LSIClassCentreDB.lsiClass("LANDSTRASSE"));
        standardRoadList.add(LSIClassCentreDB.lsiClass("INNERORTSTRASSE"));
        standardRoadList.add(LSIClassCentreDB.lsiClass("ERSCHLIESSUNGSWEG"));
        // add with lambda
        IntStream.of(LSIClassCentreDB.subClasses(standardRoadList.get(0)))
                .forEach(standardRoadList::add);

        return standardRoadList;
    }

    public static List<Integer> getRailLSICodeList() {
        List<Integer> railList = new ArrayList<>();
        railList.add(LSIClassCentreDB.lsiClass("MONORAIL"));
        railList.add(LSIClassCentreDB.lsiClass("TRAM_GLEISE"));
        railList.add(LSIClassCentreDB.lsiClass("GLEISKOERPER"));

        return railList;
    }
}
