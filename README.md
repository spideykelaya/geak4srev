# Geak4s

A modern Scala.js application built with Laminar and UI5 Web Components, powered by Vite for fast development and optimized builds.

## 🚀 Features

- **Scala.js 1.17.0** with **Scala 3.6.2** - Modern Scala with the latest features
- **Laminar 17.2.0** - Reactive UI library for elegant and type-safe web development
- **UI5 Web Components 2.1.0** - Professional enterprise-grade UI components
- **Circe 0.14.10** - Type-safe JSON encoding/decoding with semiauto derivation
- **SharePoint Integration** - Cloud storage with Microsoft Graph API and auto-save
- **Vite 6.0** - Lightning-fast development server with HMR (Hot Module Replacement)
- **Modern Architecture** - ES Modules, module splitting, and optimized builds

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **Java JDK 11+** (for running SBT and Scala.js compiler)
- **Node.js 18+** and **npm** (for Vite and JavaScript dependencies)
- **SBT 1.9.6+** (Scala Build Tool)

## 🛠️ Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Scala | 3.6.2 | Programming language |
| Scala.js | 1.17.0 | Scala to JavaScript compiler |
| Laminar | 17.2.0 | Reactive UI framework |
| UI5 Web Components | 2.1.0 | UI component library |
| Circe | 0.14.10 | JSON encoding/decoding |
| Microsoft Graph Client | Latest | SharePoint integration |
| MSAL Browser | Latest | Microsoft authentication |
| Vite | 6.0.0 | Build tool and dev server |
| SBT | 1.9.6 | Scala build tool |

## 📦 Installation

1. **Clone the repository** (or navigate to the project directory):
   ```bash
   cd geak4s
   ```

2. **Install JavaScript dependencies**:
   ```bash
   npm install
   ```

3. **Verify SBT installation**:
   ```bash
   sbt --version
   ```

## 🏃 Development Workflow

To run the application in development mode, you need to start **two processes** in separate terminals:

### Terminal 1: Start Scala.js Compiler (Watch Mode)

```bash
sbt ~fastLinkJS
```

This command:
- Compiles Scala code to JavaScript
- Watches for file changes and recompiles automatically
- Outputs to `target/scala-3.6.2/geak4s-fastopt/`

**Note**: Wait for the initial compilation to complete before starting Vite.

### Terminal 2: Start Vite Development Server

```bash
npm run dev
```

This command:
- Starts the Vite development server
- Enables Hot Module Replacement (HMR)
- Serves the application at `http://localhost:5173`

### 🌐 Open in Browser

Once both processes are running, open your browser and navigate to:

```
http://localhost:5173
```

You should now see the GEAK4S homepage.

## 🏗️ Building for Production

To create an optimized production build:

1. **Compile Scala.js with full optimization**:
   ```bash
   sbt fullLinkJS
   ```

2. **Build with Vite**:
   ```bash
   npm run build
   ```

3. **Preview the production build** (optional):
   ```bash
   npm run preview
   ```

The production-ready files will be in the `dist/` directory.

## 📁 Project Structure

```
geak4s/
├── build.sbt                          # SBT build configuration
├── package.json                       # Node.js dependencies and scripts
├── vite.config.js                     # Vite configuration
├── index.html                         # HTML entry point
├── main.js                            # JavaScript entry point
├── README.md                          # This file
├── .gitignore                         # Git ignore rules
├── project/
│   ├── build.properties               # SBT version
│   └── plugins.sbt                    # SBT plugins (Scala.js)
├── src/main/
│   ├── scala/com/example/geak4s/
│   │   ├── Main.scala                 # Application entry point
│   │   └── HelloWorldView.scala       # Hello World component
│   └── resources/
│       └── styles.css                 # Application styles
└── public/                            # Static assets (optional)
```

## 🎨 Key Components

### Main.scala

The application entry point that:
- Initializes the Laminar application
- Renders the main page layout
- Sets up the UI5 Bar header with navigation
- Provides workflow-based GEAK assessment interface

### WorkflowView.scala

The main workflow interface featuring:
- Step-by-step GEAK assessment process
- Project information management
- Building envelope data entry
- HVAC and energy systems
- XML export functionality

### styles.css

Comprehensive styling with:
- CSS Custom Properties for theming
- Responsive design for mobile devices
- Dark mode support
- Smooth animations and transitions
- Professional card-based layout

## 🔧 Configuration

### Module Splitting

The project uses Scala.js module splitting for optimized loading:

```scala
scalaJSLinkerConfig ~= {
  _.withModuleKind(ModuleKind.ESModule)
    .withModuleSplitStyle(
      ModuleSplitStyle.SmallModulesFor(List("geak4s"))
    )
}
```

### UI5 Web Components

UI5 components are imported in `main.js`:

```javascript
import "@ui5/webcomponents/dist/Assets.js";
import "@ui5/webcomponents-fiori/dist/Assets.js";
import "@ui5/webcomponents-icons/dist/Assets.js";
```

## 📚 Available Libraries

### UI5 Web Components

This project includes the following UI5 Web Components packages:

- **@ui5/webcomponents** - Core components (Button, Input, Card, etc.)
- **@ui5/webcomponents-fiori** - Fiori-specific components (Bar, ShellBar, etc.)
- **@ui5/webcomponents-icons** - SAP icon library
- **@ui5/webcomponents-compat** - Compatibility layer

For full documentation, visit: [UI5 Web Components](https://sap.github.io/ui5-webcomponents/)

### Circe JSON Codecs

The application uses Circe for type-safe JSON encoding/decoding:

- **Semiauto Derivation** - Automatic codec generation for all domain models
- **Type Safety** - Compile-time verification of JSON structure
- **Error Handling** - Detailed error messages for invalid JSON
- **SharePoint Integration** - Used for serializing project state to cloud storage

**Example Usage**:
```scala
import pme123.geak4s.domain.JsonCodecs.given
import io.circe.syntax.*
import io.circe.parser.*

// Encoding
val project: GeakProject = ...
val jsonString = project.asJson.spaces2

// Decoding
decode[GeakProject](jsonString) match
  case Right(project) => // Success
  case Left(error) => // Parse error
```

All domain models (Project, BuildingUsage, Wall, HeatProducer, etc.) have automatic JSON codecs defined in `JsonCodecs.scala`.

For detailed information, see [CIRCE-JSON-CODECS.md](CIRCE-JSON-CODECS.md)

### SharePoint Integration

The application integrates with Microsoft SharePoint for cloud storage and collaboration:

- **Automatic Login** - No manual login required! The app automatically prompts for login when SharePoint operations are needed
- **Cloud Storage** - Save projects to SharePoint document libraries
- **Auto-Save** - Automatic synchronization with 5-second debouncing
- **OAuth 2.0 Authentication** - Secure sign-in via Microsoft Authentication Library (MSAL)
- **Microsoft Graph API** - Full access to SharePoint files and folders
- **Project Organization** - Automatic folder creation per project
- **State Persistence** - JSON serialization of complete project state using Circe
- **Offline-first** - Works without SharePoint, cloud storage is optional

**User Experience**:
1. Start using the app without logging in
2. When you save or auto-save triggers, login popup appears automatically
3. Sign in with Microsoft 365 credentials
4. Your work is automatically saved to SharePoint
5. Future saves happen seamlessly in the background

**Setup Required**: To use SharePoint integration, you need to:
1. Create an Azure AD App Registration
2. Configure API permissions (Files.ReadWrite.All, Sites.ReadWrite.All)
3. Update `SharePointConfig.scala` with your tenant ID, client ID, and site URL

For detailed setup instructions, see [SHAREPOINT-INTEGRATION.md](SHAREPOINT-INTEGRATION.md)

## 🎯 Next Steps

Here are some ideas to extend this application:

1. **Add Routing** - Integrate a routing library for multi-page navigation
2. **State Management** - Implement a more complex state management pattern
3. **API Integration** - Connect to a backend API using Fetch or Axios
4. **More Components** - Explore additional UI5 components (Tables, Charts, Dialogs)
5. **More NPM Libraries** - Integrate other JavaScript libraries via Vite and JS interop
6. **Testing** - Add unit tests with ScalaTest or uTest
7. **PWA Support** - Convert to a Progressive Web App

## 🐛 Troubleshooting

### Vite can't find the Scala.js output

**Solution**: Make sure `sbt ~fastLinkJS` has completed at least one compilation before starting Vite.



### UI5 components not rendering

**Solution**: Ensure all UI5 assets are imported in `main.js` and the page has fully loaded.

### Port 5173 already in use

**Solution**: Either stop the process using that port or configure Vite to use a different port in `vite.config.js`:

```javascript
export default defineConfig({
  plugins: [scalaJSPlugin()],
  server: {
    port: 3000
  }
});
```

### SBT compilation errors

**Solution**: Make sure you're using Java 11+ and SBT 1.9.6+. Clear the cache if needed:

```bash
sbt clean
```

### NPM dependency issues

**Solution**: Delete `node_modules` and lock files, then reinstall:

```bash
rm -rf node_modules package-lock.json
npm install
```

## 📄 License

This project is open source and available under the MIT License.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

## 👨‍💻 Author

Built with ❤️ using Scala.js, Laminar, and UI5 Web Components.

---

**Happy Coding! 🎉**

