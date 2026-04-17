Build Instructions
==================

1. Open a terminal in the directory containing the Java source files.
2. Compile the coursework using:
   javac src/*.java
3. If you are preparing the final submission ZIP, include only the required source files,
   this README, and the Wireshark capture file.


Run Instructions
================

Local smoke test:
java -cp src LocalTest

Azure smoke test:
java -cp src AzureLabTest your.email@city.ac.uk 10.x.x.x [port]

Examples:
java -cp src AzureLabTest suhayr.mohamud@city.ac.uk 10.216.34.30
java -cp src AzureLabTest suhayr.mohamud@city.ac.uk 10.216.34.30 20110


Working Functionality
=====================

- SHA-256 hashing and hex conversion
- Distance calculation
- CRN string encoding/decoding
- Name request/response (G/H)
- Nearest request/response (N/O)
- Existence request/response (E/F)
- Read request/response (R/S)
- Write request/response (W/X)
- Compare-and-swap request/response (C/D)
- Relay support (V)
- UDP request retransmission with timeout and retry limit
- Azure smoke test steps 1 to 5
- LocalTest from the starter code


Known Limitations

- The code has been tested with the LocalTest and Azure smoke test, but not against every possible hidden topology or malformed packet case.
- UDP retry handling is implemented, but packet loss, duplication, and reordering have not been stress-tested in every stressful scenarios.
- Relay works in local testing, but it has not been heavily tested with longer relay chains or very busy traffic.
- Data rebalancing when newly discovered nodes are closer is limited.

