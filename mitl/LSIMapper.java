package mitl;

import fu.keys.LSIClassCentreDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

// Maps LSI Codes to the function to use and metadata needed
public class LSIMapper {

    public static enum PaintType {
        MedicalArea(900),
        EducationArea(900),
        UniversityArea(900),
        FireDeparmentArea(900),
        PoliceArea(900),
        KindergartenArea(900),
        ThemeParkArea(900),
        HistoricOthersArea(900),
        GeneralGreen(1000),
        Naherholungsgebiet(1000),
        Sand(1250),
        Cemetery(1500),
        Forest(2000),
        Sportplatz(3000),
        Fussballplatz(3000),
        Playground(3500),
        Water(4000),
        Bridge(5000),
        Autobahn(6000),
        Bundesstrasse(6000),
        Kraftfahrstrasse(6000),
        StandardStrasse(6000),
        FeldWaldWeg(6000),
        Auffahrt(6000),
        AdditionalSmallRoads(6000),
        RailPlatform(6500),
        Rail(7000),
        FootCyclePath(7000),
        PedestrianZone(8000),
        ATM(8500),
        Overnight(8900),
        Religious(8900),
        Pharmacy(8900),
        FinanceBuilding(8900),
        Theatre(8900),
        Cinema(8900),
        ConcertHall(8900),
        Museum(8900),
        CommunityLife(8900),
        AnimalInstitutions(8900),
        Court(8900),
        CityHall(8900),
        Tower(8900),
        Building(9000),
        Unspecified0Building(9000),
        Gastronomy(9000),
        Comercial(9000),
        Hairdresser(9000),
        ClothingAndShoeShops(9000),
        UnspecifiedShop(9000),
        Bookstore(9000),
        BicycleStore(9000),
        Gallery(9000),
        Florist(9000),
        GiftShop(9000),
        Bakery(9000),
        Butcher(9000),
        SwimmingAll(10000)
        ;

        private final int z;

        // Constructor
        PaintType(int z) {
            this.z = z;
        }

        // Getter
        public int getZ() {
            return z;
        }
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

    public static List<Integer> getLSICodeListForPaintType(PaintType type) {
        List<Integer> list = new ArrayList<>();
        switch (type) {
            case Autobahn:
                return getLSICodeList(LSIClassCentreDB.lsiClass("AUTOBAHN"), false);
            case Bundesstrasse:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BUNDESSTRASSE"), false);
            case Kraftfahrstrasse:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KRAFTFAHRSTRASSE"), false);
            case StandardStrasse:
                return getStandardRoadLSICodeList();
            case FeldWaldWeg:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("FELD_WALD_WEG"), false);
                list.add(LSIClassCentreDB.lsiClass("FELD_WALD_WEG_HISTORISCH"));
                return list;
            case AdditionalSmallRoads:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("ZUFAHRT"), false);
                list.add(LSIClassCentreDB.lsiClass("PARKPLATZWEG"));
                return list;
            case Auffahrt:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KREUZUNGEN_KREISEL_AUFFAHRTEN"), true);
            case Rail:
                return getRailLSICodeList();
            case GeneralGreen:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("VEGETATION"), true);
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("WALD"), true));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("AGRICULTURAL"), true));
                list.add(LSIClassCentreDB.lsiClass("WEIDELAND"));
                list.add(LSIClassCentreDB.lsiClass("GRUENFLAECHE"));
                list.add(LSIClassCentreDB.lsiClass("PARK"));
                list.add(LSIClassCentreDB.lsiClass("GARTEN"));
                list.add(LSIClassCentreDB.lsiClass("HUNDEPARK"));
                list.add(LSIClassCentreDB.lsiClass("GENERAL_PUBLIC_PLACE"));
                list.removeIf(x -> x == LSIClassCentreDB.lsiClass("WATT"));
                return list;
            case FootCyclePath:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("FAHRRAD_FUSS_WEGE_ALL"), true);
                list.removeIf(x -> x == LSIClassCentreDB.lsiClass("FUSSGAENGERZONE"));
                return list;
            case Overnight:
                return getLSICodeList(LSIClassCentreDB.lsiClass("UEBERNACHTUNGEN"), true);
            case Building:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("BUILDING"), true);
                return list;
            case Water:
                return getLSICodeList(LSIClassCentreDB.lsiClass("WATER"), true);
            case Bridge:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BRIDGE"), true);
            case Religious:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("KIRCHLICH"), true);
                list.add(LSIClassCentreDB.lsiClass("KIRCHE_HISTORIC"));
                list.add(LSIClassCentreDB.lsiClass("KLOSTER_HISTORIC"));
                return list;
            case Naherholungsgebiet:
                return getLSICodeList(LSIClassCentreDB.lsiClass("NAHERHOLUNGSGEBIET"), false);
            case Sportplatz:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("SPORTPLATZ"), false);
                list.add(LSIClassCentreDB.lsiClass("GOLFPLATZ"));
                return list;
            case Fussballplatz:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FUSSBALLPLATZ"), false);
            case Forest:
                return getLSICodeList(LSIClassCentreDB.lsiClass("WALD"), true);
            case PedestrianZone:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FUSSGAENGERZONE"), false);
            case Cemetery:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("FRIEDHOF"), false);
                list.add(LSIClassCentreDB.lsiClass("FRIEDHOF_HISTORISCH"));
                return list;
            case MedicalArea:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("KRANKENHAUS"), false);
                list.add(LSIClassCentreDB.lsiClass("KRANKENHAUS_ALLGEMEIN"));
                return list;
            case EducationArea:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("GRUND_SEKUNDARSCHULE"), true);
                list.add(LSIClassCentreDB.lsiClass("BERUFSSCHULE"));
                list.add(LSIClassCentreDB.lsiClass("EDUCATION"));
                list.addAll(getLSICodeList(LSIClassCentreDB.lsiClass("BESONDERE_SCHULE"), true));
                return list;
            case UniversityArea:
                return getLSICodeList(LSIClassCentreDB.lsiClass("UNIVERSITY"), true);
            case Pharmacy:
                return getLSICodeList(LSIClassCentreDB.lsiClass("APOTHEKE"), false);
            case Unspecified0Building:
                return new ArrayList<>();
            case FinanceBuilding:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("BANK_KREDITUNTERNEHMEN"), true);
                list.removeIf(x -> x == LSIClassCentreDB.lsiClass("GELDAUTOMAT"));
                return list;
            case ATM:
                return getLSICodeList(LSIClassCentreDB.lsiClass("GELDAUTOMAT"), false);
            case FireDeparmentArea:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FEUERWEHR"), false);
            case PoliceArea:
                return getLSICodeList(LSIClassCentreDB.lsiClass("POLIZEI"), false);
            case Theatre:
                return getLSICodeList(LSIClassCentreDB.lsiClass("THEATER"), false);
            case Cinema:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KINO"), false);
            case ConcertHall:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KONZERTHAUS"), false);
            case Museum:
                return getLSICodeList(LSIClassCentreDB.lsiClass("MUSEUM_ALL"), true);
            case AnimalInstitutions:
                return getLSICodeList(LSIClassCentreDB.lsiClass("TIERPARK"), true);
            case ThemeParkArea:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FREIZEIPARK"), true);
            case KindergartenArea:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BETREUUNG_KINDER"), true);
            case Court:
                return getLSICodeList(LSIClassCentreDB.lsiClass("GERICHT"), false);
            case CityHall:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("RATHAUS"), false);
                list.add(LSIClassCentreDB.lsiClass("RATHAUS_HISTORIC"));
                return list;
            case Playground:
                return getLSICodeList(LSIClassCentreDB.lsiClass("SPIELPLATZ"), false);
            case Gastronomy:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("GASTRONOMY"), true);
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("GASTRONOMY_AREA"), true));
                return list;
            case Comercial:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("COMMERCIAL"), true);
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("GASTRONOMY"), true));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("SHOP"), false));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("FRISOER"), false));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("KLEIDUNG"), true));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("BUCHGESCHAEFT"), false));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("FAHRRADGESCHAEFT_ALL"), true));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("GELDAUTOMAT"), false));
                list.removeAll(getLSICodeList(LSIClassCentreDB.lsiClass("BANK_KREDITUNTERNEHMEN"), true));

                return list;
            case SwimmingAll:
                return getLSICodeList(LSIClassCentreDB.lsiClass("SCHWIMMBAD_ALL"), true);
            case RailPlatform:
                // Todo - Bahnhof?
                return getLSICodeList(LSIClassCentreDB.lsiClass("BAHNSTEIG"), false);
            case Sand:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("SAND"), false);
                list.add(LSIClassCentreDB.lsiClass("STRAND"));
                list.add(LSIClassCentreDB.lsiClass("GOLFPLATZ_BUNKER"));
                list.add(LSIClassCentreDB.lsiClass("BEACHVOLLEYBALL"));
                return list;
            case CommunityLife:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("GEMEINWESEN"), true);
                return list;
            case Tower:
                return getLSICodeList(LSIClassCentreDB.lsiClass("TURM"), true);
            case HistoricOthersArea:
                list = getLSICodeList(LSIClassCentreDB.lsiClass("HISTORIC"), true);
                list.removeIf(x -> x == LSIClassCentreDB.lsiClass("KIRCHE_HISTORIC"));
                list.removeIf(x -> x == LSIClassCentreDB.lsiClass("KLOSTER_HISTORIC"));
                list.removeIf(x -> x == LSIClassCentreDB.lsiClass("RATHAUS_HISTORIC"));
                return list;
            case Hairdresser:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FRISOER"), false);
            case ClothingAndShoeShops:
                return getLSICodeList(LSIClassCentreDB.lsiClass("KLEIDUNG"), true);
            case UnspecifiedShop:
                return getLSICodeList(LSIClassCentreDB.lsiClass("SHOP"), false);
            case Bookstore:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BUCHGESCHAEFT"), false);
            case BicycleStore:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FAHRRADGESCHAEFT_ALL"), true);
            case Gallery:
                return getLSICodeList(LSIClassCentreDB.lsiClass("GALERIE"), false);
            case Florist:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FLORIST"), false);
            case GiftShop:
                return getLSICodeList(LSIClassCentreDB.lsiClass("GESCHENKARTIKEL"), false);
            case Bakery:
                return getLSICodeList(LSIClassCentreDB.lsiClass("BAECKER"), false);
            case Butcher:
                return getLSICodeList(LSIClassCentreDB.lsiClass("FLEISCHER"), false);
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
        standardRoadList.removeIf(x -> x == LSIClassCentreDB.lsiClass("BUNDESSTRASSE"));

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
