import os
import shutil
import sys
from command import Command
from pathlib import Path
from typing import Union


DATA_FILE = Path(os.path.dirname(os.path.realpath(__file__)) + '/data/data.json').resolve()
assert DATA_FILE.is_file()

JAVA_BENCHMARK_FILE = Path(os.path.dirname(os.path.realpath(__file__)) + '/Main.java').resolve()
assert JAVA_BENCHMARK_FILE.is_file()

ENGINE_VERSIONS = Path(os.path.dirname(os.path.realpath(__file__)) + '/../engine_versions').resolve()
assert ENGINE_VERSIONS.is_dir()

PACKAGE_NAME = '/src/main/java/za/ac/sun/cs/regex/'


def get_last(path: Union[str, Path]):
    if not isinstance(path, str):
        path = str(path)
    return path[path.rfind('/')+1:]


def run_version_benchmarks(path_to_src: Path):
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

    benchmark_jar = '{}/target/java-regex-v{}-1.0-SNAPSHOT-jar-with-dependencies.jar {} {}'\
        .format(path_to_src, version_nr, version_nr, DATA_FILE)
    cmd = Command('java -jar {}'.format(benchmark_jar), path_to_src)
    print('Running {}'.format(cmd.cmd))
    sig, _, _ = cmd.run()
    if sig != 0:
        print('\'{}\' returned exit code {}'.format(cmd.cmd, sig))
        sys.exit(2)

    Command('mvn clean', path_to_src).run()
    os.remove(tmp_java_file)
    os.chdir(curr_dir)


def run_benchmarks():
    for version in os.listdir(ENGINE_VERSIONS):
        run_version_benchmarks(Path('{}/{}'.format(ENGINE_VERSIONS, version)))


def main():
    run_benchmarks()


if __name__ == '__main__':
    main()
