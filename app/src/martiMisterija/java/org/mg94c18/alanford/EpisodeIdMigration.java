package org.mg94c18.alanford;

import java.util.HashMap;
import java.util.Map;

public class EpisodeIdMigration {
    private static Map<String, String> MIGRATION_MAP = null;

    public synchronized static Map<String, String> getMigrationMap() {
        if (MIGRATION_MAP != null) {
            return MIGRATION_MAP;
        }

        MIGRATION_MAP = new HashMap<>();
        // gs | grep renamed | sed -e 's/ \->\ .*\//, "/' | sed -e 's/.*\//MIGRATION_MAP.put("/' | sed -e 's/,/",/' | tr '\n' '#' | sed -e 's/#/");#/g' | tr '#' '\n'
        MIGRATION_MAP.put("9_10_b", "10");
        MIGRATION_MAP.put("101_102_a", "101");
        MIGRATION_MAP.put("101_102_b", "102");
        MIGRATION_MAP.put("104_106_a", "104");
        MIGRATION_MAP.put("104_106_b", "105");
        MIGRATION_MAP.put("104_106_c", "106");
        MIGRATION_MAP.put("106_108_a", "107");
        MIGRATION_MAP.put("106_108_b", "108");
        MIGRATION_MAP.put("109_111_a", "109");
        MIGRATION_MAP.put("11_12_a", "11");
        MIGRATION_MAP.put("109_111_b", "110");
        MIGRATION_MAP.put("111_113_a", "111");
        MIGRATION_MAP.put("111_113_b", "112");
        MIGRATION_MAP.put("111_113_c", "113");
        MIGRATION_MAP.put("11_12_b", "12");
        MIGRATION_MAP.put("13_14_a", "13");
        MIGRATION_MAP.put("13_14_b", "14");
        MIGRATION_MAP.put("15_16_a", "15");
        MIGRATION_MAP.put("15_16_b", "16");
        MIGRATION_MAP.put("17_19_a", "17");
        MIGRATION_MAP.put("17_19_b", "18");
        MIGRATION_MAP.put("19_21_a", "19");
        MIGRATION_MAP.put("2_3", "2");
        MIGRATION_MAP.put("19_21_b", "20");
        MIGRATION_MAP.put("21_22", "21");
        MIGRATION_MAP.put("22_24_a", "22");
        MIGRATION_MAP.put("22_24_b", "23");
        MIGRATION_MAP.put("24_26_a", "24");
        MIGRATION_MAP.put("24_26_b", "25");
        MIGRATION_MAP.put("24_26_c", "26");
        MIGRATION_MAP.put("26_27", "27");
        MIGRATION_MAP.put("27_28", "27bis");
        MIGRATION_MAP.put("28_30_a", "28");
        MIGRATION_MAP.put("28_30_b", "29");
        MIGRATION_MAP.put("30_31_a", "30");
        MIGRATION_MAP.put("30_31_b", "31");
        MIGRATION_MAP.put("31_32", "32");
        MIGRATION_MAP.put("34_35", "34");
        MIGRATION_MAP.put("35_36_a", "35");
        MIGRATION_MAP.put("35_36_b", "36");
        MIGRATION_MAP.put("36_38_a", "37");
        MIGRATION_MAP.put("36_38_b", "38");
        MIGRATION_MAP.put("38_40", "39");
        MIGRATION_MAP.put("40_42_a", "40");
        MIGRATION_MAP.put("40_42_b", "41");
        MIGRATION_MAP.put("42_43_a", "42");
        MIGRATION_MAP.put("42_43_b", "43");
        MIGRATION_MAP.put("43_44", "44");
        MIGRATION_MAP.put("44_46_a", "45");
        MIGRATION_MAP.put("44_46_b", "46");
        MIGRATION_MAP.put("46_48", "47");
        MIGRATION_MAP.put("48_50_a", "48");
        MIGRATION_MAP.put("48_50_b", "49");
        MIGRATION_MAP.put("4_5", "5");
        MIGRATION_MAP.put("50_51_a", "50");
        MIGRATION_MAP.put("50_51_b", "51");
        MIGRATION_MAP.put("51_52", "52");
        MIGRATION_MAP.put("52_54", "53");
        MIGRATION_MAP.put("54_55", "54");
        MIGRATION_MAP.put("55_56", "55");
        MIGRATION_MAP.put("55_58_a", "56");
        MIGRATION_MAP.put("56_58_b", "57");
        MIGRATION_MAP.put("56_58_c", "58");
        MIGRATION_MAP.put("58_60", "59");
        MIGRATION_MAP.put("6_7", "6");
        MIGRATION_MAP.put("60_61_a", "60");
        MIGRATION_MAP.put("60_61_b", "61");
        MIGRATION_MAP.put("61_62", "62");
        MIGRATION_MAP.put("62_64", "63");
        MIGRATION_MAP.put("64_65_a", "64");
        MIGRATION_MAP.put("64_65_b", "65");
        MIGRATION_MAP.put("66_67_a", "66");
        MIGRATION_MAP.put("66_67_b", "67");
        MIGRATION_MAP.put("67_69", "68");
        MIGRATION_MAP.put("69_71_a", "69");
        MIGRATION_MAP.put("7_9_a", "7");
        MIGRATION_MAP.put("69_71_b", "70");
        MIGRATION_MAP.put("71_72_a", "71");
        MIGRATION_MAP.put("71_72_b", "72");
        MIGRATION_MAP.put("73_74_a", "73");
        MIGRATION_MAP.put("73_74_b", "74");
        MIGRATION_MAP.put("74_75", "75");
        MIGRATION_MAP.put("76_77_a", "76");
        MIGRATION_MAP.put("76_77_b", "77");
        MIGRATION_MAP.put("77_79", "78");
        MIGRATION_MAP.put("79_80_a", "79");
        MIGRATION_MAP.put("7_9_b", "8");
        MIGRATION_MAP.put("79_80_b", "80");
        MIGRATION_MAP.put("80_81", "81");
        MIGRATION_MAP.put("81_83_a", "82");
        MIGRATION_MAP.put("81_83_b", "83");
        MIGRATION_MAP.put("83_85", "84");
        MIGRATION_MAP.put("85_86_a", "85");
        MIGRATION_MAP.put("85_86_b", "86");
        MIGRATION_MAP.put("87_88", "87");
        MIGRATION_MAP.put("88_90_a", "88");
        MIGRATION_MAP.put("88_90_b", "89");
        MIGRATION_MAP.put("9_10_a", "9");
        MIGRATION_MAP.put("90_92_a", "90");
        MIGRATION_MAP.put("90_92_b", "91");
        MIGRATION_MAP.put("92_93", "92");
        MIGRATION_MAP.put("93_94_a", "93");
        MIGRATION_MAP.put("94_96_a", "94");
        MIGRATION_MAP.put("94_96_b", "95");
        MIGRATION_MAP.put("94_96_c", "96");
        MIGRATION_MAP.put("97_98_a", "97");
        MIGRATION_MAP.put("97_98_b", "98");
        MIGRATION_MAP.put("98_99", "99");
        return MIGRATION_MAP;
    }
}
