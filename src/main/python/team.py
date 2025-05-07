from crewai import Agent, Crew, Process, Task
from crewai_tools import FileWriterTool, CodeInterpreterTool
from dotenv import load_dotenv
import os
import json
import prompts
import configparser


load_dotenv()

file_writer = FileWriterTool()
shell = CodeInterpreterTool()

# Read config.properties to get base_path
config = configparser.ConfigParser()
config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'main', 'resources', 'config.properties')
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
agents = []
manager_agent = None

for agent_data in crew_data:
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
    tools = []
    if "Software Engineer" in role:
        tools = [file_writer]
    elif "QA Engineer" in role:
        tools = [file_writer, shell]

    # Create agent
    agent = Agent(
        role=role,
        goal=goal,
        backstory=backstory,
        verbose=True,
        allow_delegation="Team Lead" in role,  # Only Team Lead can delegate
        tools=tools,
        llm="vertex_ai/gemini-2.0-flash-001"
    )

    # Assign as manager or regular agent
    if "Team Lead" in role:
        manager_agent = agent
    else:
        agents.append(agent)

task = Task(
    description="Create console TicTacToe game",
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

result = crew.kickoff()
print("Final result:", result)
