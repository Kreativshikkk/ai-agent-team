import configparser
import json
import os
import subprocess
import sys

from crewai import Agent, Crew, Process, Task
from crewai_tools import FileWriterTool, FileReadTool
from dotenv import load_dotenv

import prompts

load_dotenv()

file_writer = FileWriterTool()
file_read = FileReadTool()

# Read config.properties to get base_path
config = configparser.ConfigParser()
config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'main',
                           'resources', 'config.properties')
with open(config_path, 'r') as config_file:
    # Add a section header since ConfigParser requires sections
    config_content = '[DEFAULT]\n' + config_file.read()
    config.read_string(config_content)

base_path = config['DEFAULT']['base_path']
crew_json_path = os.path.join(base_path, 'src', 'main', 'python', 'crew.json')

# Load agents from crew.json
with open(crew_json_path, 'r') as f:
    crew_data = json.load(f)

# Create agents from crew.json
project_path = os.path.join(crew_data[0]["projectPath"], '.temp')
agents = []
manager_agent = None

for agent_data in crew_data[1:]:
    role = agent_data["role"]
    goal = agent_data["goal"]

    # Determine backstory based on role
    backstory = ""
    if hasattr(prompts, f"{role.lower().replace(' ', '_')}_prompt"):
        # If role exists in prompts.py, use that prompt and append custom backstory
        prompt_attr = f"{role.lower().replace(' ', '_')}_prompt"
        backstory = getattr(prompts, prompt_attr)
        backstory += f"\n\ncustom prompt: {agent_data['backstory']}"
    else:
        # If role doesn't exist in prompts.py, use backstory from crew.json
        backstory = agent_data["backstory"]

    # Determine tools based on role
    tools = [] if "Team Lead" in role else [file_writer, file_read]

    # Create agent
    agent = Agent(
        role=role,
        goal=goal,
        backstory=backstory,
        verbose=True,
        allow_delegation=("Team Lead" or "QA Engineer") in role,  # Only Team Lead can delegate
        tools=tools,
        llm="vertex_ai/gemini-2.0-flash-001"
    )

    # Assign as manager or regular agent
    if "Team Lead" in role:
        manager_agent = agent
    else:
        agents.append(agent)

# Get task description from command line argument if provided, otherwise use default
task_description = sys.argv[1] if len(sys.argv) > 1 else "Create console TicTacToe game"

task = Task(
    description=task_description,
    expected_output="Working implementation and passing tests"
)

task_debug = Task(
    description="Analyze errors and fix them!",
    expected_output="Working implementation and passing tests"
)

crew = Crew(
    agents=agents,
    manager_agent=manager_agent,
    tasks=[task],
    process=Process.hierarchical,
    planning=True,
    verbose=True
)

team_lead_debug = Agent(
    role="Team Lead",
    goal="Debug and fix bugs in the code",
    backstory=prompts.team_lead_debug_prompt,
    verbose=True,
    allow_delegation=True,
    llm="vertex_ai/gemini-2.0-flash-001"
)

debug_crew = Crew(
    agents=agents,
    manager_agent=team_lead_debug,
    tasks=[task_debug],
    process=Process.hierarchical,
    planning=True,
    verbose=True
)

os.chdir(project_path)

if __name__ == "__main__":
    previous_errors = None
    while True:
        if previous_errors:
            print("💥 Tests failed, feeding errors back into kickoff…")
            with open("test_errors.txt", "w") as f:
                f.write(previous_errors)
            result = debug_crew.kickoff(inputs={"test_errors": previous_errors})
        else:
            print("🚀 First iteration: kickoff…")
            result = crew.kickoff()

        print("🔄 Iteration result:", result)

        # RUN TESTS with subprocess
        print("🧪 Running pytest via subprocess…")
        proc = subprocess.run(
            ["pytest", "--disable-warnings", "-q"],
            capture_output=True, text=True
        )
        output = proc.stdout + proc.stderr
        print(output)

        # CHECK results
        if "failed" in output.lower() or "error" in output.lower():
            previous_errors = output
            print("😱 Still broken – looping again…")
            continue
        else:
            print("🎉 All tests passed! Mission accomplished. 🤘")
            break
