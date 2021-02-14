import os
import shutil
import itertools
from command import Command
from pathlib import Path
from typing import List, Iterator, Union

ENGINE_VERSIONS = os.path.dirname(os.path.realpath(__file__)) + '/../engine_versions'
ENGINE_VERSIONS = Path(ENGINE_VERSIONS).resolve()

DIFFS_OUTPUT = Path(os.path.dirname(os.path.realpath(__file__)) + '/diffs').resolve()


def get_src_files(root: Path) -> Iterator[Path]:
    assert root.is_dir()
    for dirpath, _, filenames in os.walk(str(root)):
        if dirpath.endswith('tests'):
            continue

        for fn in filenames:
            if fn.endswith('java'):
                yield Path('{}/{}'.format(dirpath, fn)).resolve()


def get_last(path: Union[str, Path]):
    if not isinstance(path, str):
        path = str(path)
    return path[path.rfind('/')+1:]


def compute_diff(root_dir: Path, src_file1: Path, src_file2: Path):
    assert get_last(src_file1) == get_last(src_file2)
    filename = get_last(src_file1)

    cmd = Command('./diffutil.sh {} {}'.format(str(src_file1), str(src_file2)), os.getcwd())
    sig, out, err = cmd.run()

    diff_file = '{}/{}'.format(str(root_dir), filename.replace('.java', '.diff'))
    with open(diff_file, 'w') as f:
        f.write(out)
        print('Diff for {} written to {}!'.format(filename, diff_file))


def compute_diffs(root_dir: Path, src_files1: List[Path], src_files2: List[Path]):
    src_files1 = sorted([s for s in src_files1 if any([get_last(s) in map(get_last, src_files2)])])
    src_files2 = sorted([s for s in src_files2 if any([get_last(s) in map(get_last, src_files1)])])
    for s1, s2 in itertools.zip_longest(sorted(src_files1), sorted(src_files2)):
        if get_last(s1) != get_last(s2):
            continue

        compute_diff(root_dir, s1, s2)


def main():
    version_src_files = []
    for version in os.listdir(ENGINE_VERSIONS):
        v_root = Path('{}/{}'.format(ENGINE_VERSIONS, version))
        src_files = list(get_src_files(v_root))
        version_src_files.append((int(version), src_files))

    version_src_files.sort(key=lambda e: e[0])

    print(version_src_files)
    assert DIFFS_OUTPUT.is_dir()

    for i in range(0, len(version_src_files)):
        if not (i + 1 < len(version_src_files)): break

        version1, src1 = version_src_files[i]
        version2, src2 = version_src_files[i+1]
        diff_dir = Path('{}/{}to{}'.format(DIFFS_OUTPUT, version1, version2))
        if diff_dir.exists():
            shutil.rmtree(diff_dir)
        diff_dir.mkdir()

        compute_diffs(diff_dir, src1, src2)


if __name__ == '__main__':
    main()
