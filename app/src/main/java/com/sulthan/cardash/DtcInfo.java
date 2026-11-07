package com.sulthan.cardash;

import java.util.HashMap;
import java.util.Map;

/**
 * Plain-words meanings for fault codes. The generic (SAE J2012) codes that turn up most often are
 * listed one by one; any other code gets the meaning of its group (for example "P03xx: ignition or
 * misfire"). Codes whose third character is not 0 are maker-specific and are labelled so.
 */
final class DtcInfo {
    private static final Map<String, String> CODES = new HashMap<>();

    private static void c(String code, String text) { CODES.put(code, text); }

    static {
        // Fuel and air metering
        c("P0010", "Camshaft timing actuator circuit (bank 1)");
        c("P0011", "Camshaft timing too far advanced (bank 1)");
        c("P0012", "Camshaft timing too far retarded (bank 1)");
        c("P0016", "Crankshaft and camshaft positions don't agree (bank 1)");
        c("P0030", "Oxygen sensor heater circuit (bank 1, sensor 1)");
        c("P0036", "Oxygen sensor heater circuit (bank 1, sensor 2)");
        c("P0087", "Fuel rail pressure too low");
        c("P0088", "Fuel rail pressure too high");
        c("P0100", "Mass air flow sensor circuit");
        c("P0101", "Mass air flow sensor reading out of range");
        c("P0102", "Mass air flow sensor signal low");
        c("P0103", "Mass air flow sensor signal high");
        c("P0105", "Manifold pressure (MAP) sensor circuit");
        c("P0106", "Manifold pressure (MAP) sensor reading out of range");
        c("P0107", "Manifold pressure (MAP) sensor signal low");
        c("P0108", "Manifold pressure (MAP) sensor signal high");
        c("P0110", "Intake air temperature sensor circuit");
        c("P0112", "Intake air temperature sensor signal low");
        c("P0113", "Intake air temperature sensor signal high");
        c("P0115", "Engine coolant temperature sensor circuit");
        c("P0116", "Engine coolant temperature sensor reading out of range");
        c("P0117", "Engine coolant temperature sensor signal low");
        c("P0118", "Engine coolant temperature sensor signal high");
        c("P0120", "Throttle position sensor circuit");
        c("P0121", "Throttle position sensor reading out of range");
        c("P0122", "Throttle position sensor signal low");
        c("P0123", "Throttle position sensor signal high");
        c("P0125", "Engine takes too long to warm up for fuel control");
        c("P0128", "Engine runs too cold (thermostat likely stuck open)");
        c("P0130", "Oxygen sensor circuit (bank 1, sensor 1)");
        c("P0131", "Oxygen sensor low voltage (bank 1, sensor 1)");
        c("P0132", "Oxygen sensor high voltage (bank 1, sensor 1)");
        c("P0133", "Oxygen sensor slow response (bank 1, sensor 1)");
        c("P0134", "Oxygen sensor no activity (bank 1, sensor 1)");
        c("P0135", "Oxygen sensor heater circuit (bank 1, sensor 1)");
        c("P0136", "Oxygen sensor circuit (bank 1, sensor 2)");
        c("P0137", "Oxygen sensor low voltage (bank 1, sensor 2)");
        c("P0138", "Oxygen sensor high voltage (bank 1, sensor 2)");
        c("P0139", "Oxygen sensor slow response (bank 1, sensor 2)");
        c("P0140", "Oxygen sensor no activity (bank 1, sensor 2)");
        c("P0141", "Oxygen sensor heater circuit (bank 1, sensor 2)");
        c("P0171", "Engine running lean (bank 1): too much air or too little fuel, often a vacuum leak");
        c("P0172", "Engine running rich (bank 1): too much fuel");
        c("P0174", "Engine running lean (bank 2)");
        c("P0175", "Engine running rich (bank 2)");
        c("P0190", "Fuel rail pressure sensor circuit");
        c("P0200", "Injector circuit");
        c("P0201", "Injector circuit, cylinder 1");
        c("P0202", "Injector circuit, cylinder 2");
        c("P0203", "Injector circuit, cylinder 3");
        c("P0204", "Injector circuit, cylinder 4");
        c("P0217", "Engine overheating");
        c("P0219", "Engine over-revved");
        c("P0221", "Throttle position sensor B reading out of range");
        c("P0230", "Fuel pump circuit");
        c("P0234", "Turbo or supercharger overboost");
        c("P0299", "Turbo or supercharger underboost");
        // Ignition and misfire
        c("P0300", "Random or multiple cylinder misfire");
        c("P0301", "Misfire in cylinder 1");
        c("P0302", "Misfire in cylinder 2");
        c("P0303", "Misfire in cylinder 3");
        c("P0304", "Misfire in cylinder 4");
        c("P0305", "Misfire in cylinder 5");
        c("P0306", "Misfire in cylinder 6");
        c("P0325", "Knock sensor circuit (bank 1)");
        c("P0327", "Knock sensor signal low (bank 1)");
        c("P0328", "Knock sensor signal high (bank 1)");
        c("P0335", "Crankshaft position sensor circuit");
        c("P0336", "Crankshaft position sensor reading out of range");
        c("P0340", "Camshaft position sensor circuit");
        c("P0341", "Camshaft position sensor reading out of range");
        c("P0351", "Ignition coil circuit, cylinder 1");
        c("P0352", "Ignition coil circuit, cylinder 2");
        c("P0353", "Ignition coil circuit, cylinder 3");
        c("P0354", "Ignition coil circuit, cylinder 4");
        // Emissions controls
        c("P0400", "Exhaust gas recirculation (EGR) flow");
        c("P0401", "Exhaust gas recirculation (EGR) flow too low");
        c("P0402", "Exhaust gas recirculation (EGR) flow too high");
        c("P0403", "Exhaust gas recirculation (EGR) circuit");
        c("P0404", "Exhaust gas recirculation (EGR) reading out of range");
        c("P0410", "Secondary air injection system");
        c("P0420", "Catalytic converter efficiency below threshold (bank 1)");
        c("P0421", "Catalytic converter warm-up efficiency below threshold (bank 1)");
        c("P0430", "Catalytic converter efficiency below threshold (bank 2)");
        c("P0440", "Fuel vapour (EVAP) system");
        c("P0441", "Fuel vapour (EVAP) purge flow incorrect");
        c("P0442", "Fuel vapour (EVAP) small leak, often a loose fuel cap");
        c("P0443", "Fuel vapour (EVAP) purge valve circuit");
        c("P0446", "Fuel vapour (EVAP) vent control circuit");
        c("P0449", "Fuel vapour (EVAP) vent valve circuit");
        c("P0451", "Fuel vapour (EVAP) pressure sensor reading out of range");
        c("P0455", "Fuel vapour (EVAP) large leak, often a missing or loose fuel cap");
        c("P0456", "Fuel vapour (EVAP) very small leak");
        c("P0457", "Fuel vapour (EVAP) leak: fuel cap loose or off");
        c("P0461", "Fuel level sensor reading out of range");
        c("P0480", "Cooling fan relay circuit");
        // Speed, idle and auxiliary inputs
        c("P0500", "Vehicle speed sensor");
        c("P0501", "Vehicle speed sensor reading out of range");
        c("P0505", "Idle air control system");
        c("P0506", "Idle speed lower than expected");
        c("P0507", "Idle speed higher than expected");
        c("P0520", "Engine oil pressure sensor circuit");
        c("P0521", "Engine oil pressure sensor reading out of range");
        c("P0530", "Air conditioning refrigerant pressure sensor circuit");
        c("P0560", "System voltage");
        c("P0562", "System voltage low (battery or alternator)");
        c("P0563", "System voltage high (alternator overcharging)");
        c("P0571", "Brake switch circuit");
        // Computer and outputs
        c("P0600", "Communication link between control modules");
        c("P0601", "Engine computer memory checksum error");
        c("P0602", "Engine computer programming error");
        c("P0603", "Engine computer keep-alive memory error (often after a battery disconnect)");
        c("P0604", "Engine computer RAM error");
        c("P0605", "Engine computer ROM error");
        c("P0606", "Engine computer processor fault");
        c("P0607", "Engine computer performance");
        c("P0620", "Alternator control circuit");
        c("P0627", "Fuel pump control circuit");
        c("P0641", "Sensor reference voltage A circuit");
        c("P0651", "Sensor reference voltage B circuit");
        c("P0700", "Transmission control system fault (read the transmission computer for details)");
        c("P0705", "Transmission range (gear selector) sensor circuit");
        c("P0715", "Transmission input speed sensor circuit");
        c("P0720", "Transmission output speed sensor circuit");
        c("P0730", "Incorrect gear ratio");
        c("P0740", "Torque converter clutch circuit");
        c("P0741", "Torque converter clutch stuck off");
        c("P0750", "Shift solenoid A");
        c("P0755", "Shift solenoid B");
        // Network
        c("U0001", "High-speed CAN communication bus");
        c("U0073", "Control module communication bus off");
        c("U0100", "Lost communication with the engine computer");
        c("U0101", "Lost communication with the transmission computer");
        c("U0121", "Lost communication with the ABS module");
        c("U0140", "Lost communication with the body control module");
        c("U0155", "Lost communication with the instrument cluster");
    }

    /** A one-line meaning for [code], never null. */
    static String describe(String code) {
        if (code == null || code.length() != 5) return "Unknown code";
        String known = CODES.get(code);
        if (known != null) return known;
        char sys = code.charAt(0);
        char kind = code.charAt(1);
        if (kind != '0' && kind != '2') {
            return makerSystem(sys) + ": maker-specific code, look it up for your car model";
        }
        if (sys == 'P') {
            switch (code.charAt(2)) {
                case '0':
                case '1':
                case '2': return "Fuel and air metering";
                case '3': return "Ignition system or misfire";
                case '4': return "Emissions control";
                case '5': return "Speed, idle control or auxiliary inputs";
                case '6': return "Engine computer or its outputs";
                case '7':
                case '8':
                case '9': return "Transmission";
                default: return "Engine or transmission";
            }
        }
        return makerSystem(sys);
    }

    private static String makerSystem(char sys) {
        switch (sys) {
            case 'P': return "Engine or transmission";
            case 'C': return "Chassis (brakes, steering, suspension)";
            case 'B': return "Body (airbags, seats, lights, climate)";
            case 'U': return "Communication between control modules";
            default: return "Unknown system";
        }
    }
}
