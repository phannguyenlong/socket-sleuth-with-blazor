![https://snyk.io/test/github/snyk/socketsleuth/badge.svg](https://snyk.io/test/github/snyk/socketsleuth/badge.svg)

# LONGPN13 Note: Addition coding in compare with previous tool
1. `[NEW] src\main\java\helper`: Migrate unpack (decode) and pack (encode) Blazor message from tool Blazor Traffic Processor
2. `[Update] src\main\java\WebSocketMessageHandler.java` - Function `handleBinaryMessageReceived`: write code for handle websocket message in binary. Only code for match and replace function to use in cooperation with Blazor message.
3. Modify BTP context menu: Add "Send to BTP Tab (concat)" options to send multiple tab
4. Add tamper Money script for frontend extractor

## demo video

https://github.com/phannguyenlong/socket-sleuth-with-blazor/assets/44134735/04d0ae0e-5a94-4e27-b931-6d70baad3894

# SocketSleuth: Burp Suite Extension for websocket testing
## Overview
SocketSleuth aims to enhance Burp Suite's websocket testing capabilities and make testing websocket based applications easier. This extension is currently in beta release but contains some powerful features such as a new websocket focused history tab, match and replace rules for websockets, an intruder like utility, and a message autorepeater for authorization testing.
## Build Instructions
### Requirements
- Burp Suite Professional / Community version 2022.9.5 or later
- Maven
### Steps
1. Clone the repository
   ```
   git clone https://github.com/snyk/socketsleuth.git
   ```
2. Navigate to the project directory
   ```
   cd socketsleuth
   ```
3. Build the project using Maven
   ```
   mvn clean package
   ```
4. Load the generated JAR file (`SocketSleuth/target/SocketSleuth-[VERSION]-jar-with-dependencies.jar`) into Burp Suite via `Extensions -> Installed -> Add`.

## Features
The current features for the beta version are minimal, but should be quite powerful. 
- Websocket history
- Websocket intruder
  - JSONRPC method discovery
  - Sniper
    - Simple List
    - Numeric
- Websocket AutoRepater
  - Similar to AutoRepeater and Autoize but for websocket. Allows the contents of a source websocket to automatically be replayed in a target socket. When setup with two unique sessions, this allows for automated AuthZ testing.
- Interception Rules
- Match & Replace Rules
  - Basic string
  - Hex encoded string (useful when working with non string payloads)
  - Regex
  
# Issues
For updated list of bugs and issues see the project issues. However at launch for beta release, there is some known problems.
- Currently only supports text based websockets. Binary based messages need some refactoring and we intend to address this soon.
- Regular Expression Match & Replace rules can be flakey and doesn't work all the time. Will be fixed soon.
- Table sorting does not work. 

## Contributing
Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.
## License
SocketSleuth is under the Apache 2.0 License. See [LICENSE](LICENSE) for more information.
