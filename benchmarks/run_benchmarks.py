import os
import shutil
import sys
import json
from command import Command
from pathlib import Path
from typing import Union, List, Dict


DATA_FILE = Path(os.path.dirname(os.path.realpath(__file__)) + '/data/regexlib_analysis_results.json').resolve()
assert DATA_FILE.is_file()

JAVA_BENCHMARK_FILE = Path(os.path.dirname(os.path.realpath(__file__)) + '/resources/Main.java').resolve()
assert JAVA_BENCHMARK_FILE.is_file()

ENGINE_VERSIONS = Path(os.path.dirname(os.path.realpath(__file__)) + '/../engine_versions').resolve()
assert ENGINE_VERSIONS.is_dir()

RESULTS_DIR = Path(os.path.dirname(os.path.realpath(__file__)) + '/results/').resolve()
assert RESULTS_DIR.is_dir()

PACKAGE_NAME = '/src/main/java/za/ac/sun/cs/regex/'


def get_last(path: Union[str, Path]):
    if not isinstance(path, str):
        path = str(path)
    return path[path.rfind('/')+1:]


def run_version_benchmarks(path_to_src: Path, results: List[Dict[str, List[str]]]):
    assert path_to_src.is_dir()
    curr_dir = os.getcwd()
    os.chdir(str(path_to_src))
    tmp_java_file = str(path_to_src) + PACKAGE_NAME + JAVA_BENCHMARK_FILE.name
    shutil.copy(JAVA_BENCHMARK_FILE, tmp_java_file)

    version_nr = get_last(path_to_src)
    cmd = Command('mvn clean && mvn package', path_to_src)
    sig, _, _ = cmd.run()
    if sig != 0:
        print('\'mvn clean && mvn package\' failed for version jdk{}!'.format(version_nr))
        sys.exit(1)
    print('jdk{} compiled successfully!'.format(version_nr))

    benchmark_jar = '{}/target/java-regex-v{}-1.0-SNAPSHOT-jar-with-dependencies.jar --jsonfile {} --timeout {}'\
        .format(path_to_src, version_nr, DATA_FILE, 6)
    cmd = Command('java -jar {}'.format(benchmark_jar), path_to_src)
    print('Running {}'.format(cmd.cmd))
    sig, out, _ = cmd.run()
    if sig != 0:
        print('\'{}\' returned exit code {}'.format(cmd.cmd, sig))
        sys.exit(2)
    else:
        process_output(out.split('\n'), version_nr, results)

    Command('mvn clean', path_to_src).run()
    os.remove(tmp_java_file)
    os.chdir(curr_dir)


def run_benchmarks(results: List[Dict[str, List[str]]]):
    for version in os.listdir(ENGINE_VERSIONS):
        run_version_benchmarks(Path('{}/{}'.format(ENGINE_VERSIONS, version)), results)


def process_output(out: List[str], java_version: str, results: List[Dict[str, List[str]]]):
    current_regex, current_exploit = None, None
    times, memory = [], []
    for line in map(lambda l: l.strip(), out):
        if line.startswith('regex: '):
            current_regex = line[7:]
        elif line.startswith('exploit: '):
            current_exploit = line[9:]
        elif line == 'InterruptedException' or line == 'ExecutionException':
            print('Skipping due to unexpected exception!')
            current_regex, current_exploit = None, None
        elif line == 'TimeoutException':
            times.append('timeout')
            if current_regex is not None and current_exploit is not None:
                print('TIMED OUT: {} [exploit={}]'.format(current_regex, current_exploit))
            else:
                print('TIMED OUT: no regex or exploit available...')
            current_regex, current_exploit = None, None
        else:
            line_list = line.split()
            if len(line_list) != 2: continue
            times.append(line_list[0])
            memory.append(line_list[1])
    results.append({
        java_version: {
            'times': times,
            'memory': memory
        }
    })


def write_results(results: List[Dict[str, List[str]]]):
    with open('{}/results_{}'.format(RESULTS_DIR, DATA_FILE.name), 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2)


def main():
    results = []
    run_benchmarks(results)
    write_results(results)


if __name__ == '__main__':
    main()
