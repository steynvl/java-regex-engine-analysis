import os
from pathlib import Path
from typing import Iterator

ENGINE_VERSIONS = os.path.dirname(os.path.realpath(__file__)) + '/../engine_versions'
ENGINE_VERSIONS = Path(ENGINE_VERSIONS).resolve()


def get_src_files(root: Path) -> Iterator[Path]:
    assert root.is_dir()
    for dirpath, _, filenames in os.walk(str(root)):
        if dirpath.endswith('tests'):
            continue

        for fn in filenames:
            if fn.endswith('java'):
                yield Path('{}/{}'.format(dirpath, fn)).resolve()


def main():
    version_src_files = []
    for version in os.listdir(ENGINE_VERSIONS):
        v_root = Path('{}/{}'.format(ENGINE_VERSIONS, version))
        src_files = list(get_src_files(v_root))
        version_src_files.append((int(version), src_files))

    version_src_files.sort(key=lambda e: e[0])
    print(version_src_files)


if __name__ == '__main__':
    main()
