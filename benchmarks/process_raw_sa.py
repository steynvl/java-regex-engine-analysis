import sys
import re
import os
import json
from pathlib import Path
from typing import List, Dict


class Reader:

    def __init__(self, lines: List[str]):
        self._lines = lines
        self._idx = 0

    def next_line(self) -> str:
        line = self._lines[self._idx]
        self._idx += 1
        return line

    def peek(self) -> str:
        return self._lines[self._idx]

    def has_next(self) -> bool:
        return self._idx < len(self._lines)


def read_raw(raw_file: Path) -> Reader:
    with open(str(raw_file), 'r') as f:
        return Reader([l.rstrip() for l in f.readlines()])


def process(analysis_summary: Dict, reader: Reader):
    def parse_summary():
        while reader.has_next():
            line = reader.next_line()
            mtch = re.search(r'^Number of test cases: (\d+)', line)
            if mtch is not None:
                analysis_summary['num_test_cases'] = mtch.group(1)

            mtch = re.search(r'^Number of parsing failures: (\d+)/\d+', line)
            if mtch is not None:
                analysis_summary['num_parsing_failures'] = mtch.group(1)

            mtch = re.search(r'^Number of cases - linear: (\d+)/\d+', line)
            if mtch is not None:
                analysis_summary['num_cases_linear'] = mtch.group(1)

            mtch = re.search(r'^Number of cases - EDA: (\d+)/\d+', line)
            if mtch is not None:
                analysis_summary['num_cases_eda'] = mtch.group(1)

            mtch = re.search(r'^Number of cases - IDA: (\d+)/\d+', line)
            if mtch is not None:
                analysis_summary['num_cases_ida'] = mtch.group(1)
                break

    def parse_patterns():
        patterns = []
        num_sa_failures = 0
        while reader.has_next():
            line = reader.next_line()

            mtch = re.search(r'Number of SimpleAnalysis timeouts: (\d+)', line)
            if mtch is not None:
                analysis_summary['num_simple_analysis_timeout'] = mtch.group(1)
                continue

            if re.search(r'^\d+:', line) is None:
                continue
            current_pattern = line[line.find(' ')+1:]

            while True:
                if reader.peek().startswith('Memoization policy:'):
                    reader.next_line()
                elif reader.peek().startswith('Memoization encoding scheme:'):
                    reader.next_line()
                else:
                    break

            msg = reader.next_line()
            if msg.startswith('Simple analysis failed.'):
                num_sa_failures += 1

            if reader.peek().startswith('Number of nodes selected for memoization:'):
                reader.next_line()
                pattern = {'pattern': current_pattern}
                mtch = re.search(r'- Pivot: (\d+) \(Execution time: (\d+)ms\)', reader.next_line())
                pattern['pivot'] = get_state_and_exec(mtch)
                mtch = re.search(r'- In-degree>1: (\d+) \(Execution time: (\d+)ms\)', reader.next_line())
                pattern['indeg'] = get_state_and_exec(mtch)
                mtch = re.search(r'- Ancestor: (\d+) \(Execution time: (\d+)ms\)', reader.next_line())
                pattern['ancestor'] = get_state_and_exec(mtch)
                patterns.append(pattern)

        analysis_summary['num_simple_analysis_failures'] = num_sa_failures
        analysis_summary['patterns'] = patterns

    parse_summary()
    parse_patterns()


def get_state_and_exec(regex_match) -> Dict:
    return {
        'num_states': int(regex_match.group(1)),
        'exec_time': int(regex_match.group(2))
    }


def write_to_json(analysis_summary: Dict, target: Path):
    with open(str(target), 'w') as f:
        json.dump(analysis_summary, f, indent=2)


def main(raw_file: Path):
    raw_out = read_raw(raw_file)
    analysis_summary = {}
    process(analysis_summary, raw_out)
    target = Path('{}/analysis_summary.json'.format(os.path.abspath(os.path.dirname(str(raw_file)))))
    write_to_json(analysis_summary, target)


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print('Usage: python process_raw_sa.py <raw_output.txt>')
        sys.exit(1)

    file = Path(sys.argv[1])
    if not file.is_file():
        print('{} is not a file!'.format(file))

    main(file)
