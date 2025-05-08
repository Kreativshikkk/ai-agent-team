# AI Agent Team Plugin

## Getting Started

Follow the steps below to set up and run the AI Agent Team plugin locally.

### 1. Clone the Repository

Start by cloning the repository to your local machine:

```bash
git clone https://github.com/Kreativshikkk/ai-agent-team.git
```

### 2. Configure the Base Path

Before running the plugin, you need to create a configuration file that specifies the absolute path to your local `ai-agent-team` directory.

1. Go to the following directory:

   ```
   ai-agent-team/src/main/resources/
   ```

2. Create a file named:

   ```
   config.properties
   ```

3. Add the following line to the file:

   ```
   base_path=/absolute/path/to/ai-agent-team
   ```

   **Example:**

   ```
   base_path=/Users/yourname/IdeaProjects/ai-agent-team
   ```

   ⚠️ Be sure to replace the example with the actual full path to the `ai-agent-team` folder on your system.
### 3. Setup the Python Environment
It is highly recommended to use a virtual environment to run Python scripts properly.
1. Go to the following directory:
   ```
   ai-agent-team/src/main
   ```
2. Create a virtual environment:
   ```bash
    python3 -m venv .venv
    ```
3. Install neccesary dependencies:
    ```bash
     pip install crewai crewai-tools dotenv pytest
     ```



### 4. Run the Plugin

From the root of the project, run the following command in your terminal:

```bash
./gradlew runIde
```

This will launch IntelliJ IDEA with the plugin loaded in a sandbox environment.
