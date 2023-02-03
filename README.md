# javapers

A knowledge graph extractor for Java projects.

## Knowledge graph types

javapers can extract knowledge graph instances of the following two graph types.

1. Detailed:

   <img src="figures/detailed.svg" alt="Detailed graph type"/>

2. Abstract:

   <img src="figures/abstract.svg" alt="Abstract graph type"/>

## Usage

```shell
$ java -jar javapers.jar <args>
```

| Argument                        | Description                                                                                                                          |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `-i [path]`                     | Input: path to the source directory of the Java project. Default value is the current working directory.                             |
| `-o [path]`                     | Output: path to the directory where output file(s) will be created. Default value is the current working directory.                  |
| <code>-f [json&#124;csv]</code> | The output format. Default value is csv.                                                                                             |
| `-n [name]`                     | Output file basename. For example, when output type is json, the output file name will be [name].json. Default value is JavaProject. |
| `-c`                            | When this argument is provided, generate an abstracted knowledge graph. Otherwise, generate a detailed one.                          |

## Showcase

Consult the `sample_output` directory to get a feel of what the resulting knowledge graph may look like.

Check out [classviz](https://github.com/rsatrioadi/classviz), a project that uses an output of javapers as input for visualizing class relationships. Specifically, it takes the json-formatted output of the abstract graph type.

You can play around with the JHotDraw example [here](https://rsatrioadi.github.io/classviz/?p=jhotdraw_abstract).

You can visualize the detailed graph as well using classviz ([example](https://rsatrioadi.github.io/classviz/?p=strategy_detailed)). However, the interaction tools does not currently support the node and edge types of the detailed graph type.

## Citing

```bibtex
@software{rukmono2023javapers,
  author       = {Satrio Adi Rukmono},
  title        = {rsatrioadi/javapers: javapers 1.0},
  month        = jan,
  year         = 2023,
  publisher    = {Zenodo},
  version      = {v1.0},
  doi          = {10.5281/zenodo.7568438},
  url          = {https://doi.org/10.5281/zenodo.7568438}
}
```
