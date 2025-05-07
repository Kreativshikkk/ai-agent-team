from crewai import Agent, Crew, Process, Task
from crewai_tools import FileWriterTool, CodeInterpreterTool
from dotenv import load_dotenv
import os, prompts


load_dotenv()
api_key = os.getenv("OPENAI_API_KEY")

file_writer = FileWriterTool()
shell = CodeInterpreterTool()

team_lead = Agent(
    role="Team Lead",
    goal="Coordinate and delegate subtasks",
    backstory=prompts.team_lead_prompt,
    verbose=True,
    allow_delegation=True
)
engineer = Agent(
    role="Software Engineer",
    goal="Implement code",
    backstory=prompts.engineer_prompt,
    verbose=True,
    allow_delegation=False,
    tools=[file_writer]
)
qa_engineer = Agent(
    role="QA Engineer",
    goal="Test code artifacts, run tests and ensure quality",
    backstory=prompts.qa_prompt,
    verbose=True,
    allow_delegation=False,
    tools=[file_writer, shell]
)

task = Task(
    description="Create console TicTacToe game",
    expected_output="Working implementation and passing tests"
)

crew = Crew(
    agents=[engineer, qa_engineer],
    manager_agent=team_lead,
    tasks=[task],
    process=Process.hierarchical,
    planning=True,
    verbose=True,
)

result = crew.kickoff()
print("Final result:", result)
