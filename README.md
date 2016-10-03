# Arg: Avro Random Generator

__NOTE: Building is required to run the program.__

## What does it do?

#### The boring stuff

Arg reads a schema through either stdin or a CLI-specified file and
generates random data to fit it.

Arg can output data in either JSON or binary format, and when outputting
in JSON, can either print in compact format (one instance of spoofed
data per line) or pretty format.

Arg can output data either to stdout or a file. After outputting all of
its spoofed data, Arg prints a single newline.

The number of instances of spoofed data can also be specified; the
default is currently 1.

#### The cool stuff

Arg also allows for special annotations in the Avro schema it spoofs
that narrow down the kind of data produced. For example, when spoofing
a string, you can currently either specify a length that the string
should be (or one or both of a minimum and maximum that the length
should be), a list of possible strings that the string should come from,
or a regular expression that the string should adhere to.

These annotations are specified as JSON properties in the schema that
Arg spoofs. They should not collide with any existing properties, or
cause any issues if present when the schema is used with other programs.

## Building

```
$ ./gradlew distribute
```

## CLI Usage

<pre>
$ ./arg -?
arg: Generate random Avro data
Usage: arg [-f &lt;file&gt; | -s &lt;schema&gt;] [-j | -b] [-p | -c] [-i &lt;i&gt;] [-o &lt;file&gt;]

Flags:
    -?, -h, --help:	Print a brief usage summary and exit with status 0
    -b, --binary:	Encode outputted data in binary format
    -c, --compact:	Output each record on a single line of its own (has no effect if encoding is not JSON)
    -f &lt;file&gt;, --schema-file &lt;file&gt;:	Read the schema to spoof from &lt;file&gt;, or stdin if &lt;file&gt; is '-' (default is '-')
    -i &lt;i&gt;, --iterations &lt;i&gt;:	Output &lt;i&gt; iterations of spoofed data (default is 1)
    -j, --json:	Encode outputted data in JSON format (default)
    -o &lt;file&gt;, --output &lt;file&gt;:	Write data to the file &lt;file&gt;, or stdout if &lt;file&gt; is '-' (default is '-')
    -p, --pretty:	Output each record in prettified format (has no effect if encoding is not JSON) (default)
    -s &lt;schema&gt;, --schema &lt;schema&gt;:	Spoof the schema &lt;schema&gt;

Currently on WePay's private GitHub:
https://github.devops.wepay-inc.com/ChrisEgerton/avro-random-generator
</pre>


## Schema annotations

### Annotation types

The following annotations are currently supported:
+ __arg.options:__ Either a JSON array of possibilities that the data for
spoofing this schema should come from, or a JSON object that conforms to
the following format: `{"file": <file>, "encoding": <encoding>}` (both
fields must be specified). If given as an object, a list of data will be
read from the file after decoding with the specified format (currently
"json" and "binary" are the only supported values, and "binary" may be
somewhat buggy).
+ __arg.range:__ A JSON object that conforms to the following format:
`{"min": <min>, "max": <max>}` (at least one of "min" or "max" must be
specified). If provided, ensures that the generated number will be
greater than or equal to <min> and/or strictly less than <max>.
+ __arg.length:__ Either a JSON number or a JSON object that conforms to the
following format: `{"min": <min>, "max": <max>}` (at least one of "min"
or "max" must be specified, and if present, values for either must be
numbers). __Defaults to `{"min": 8, "max": 16}`__.
+ __arg.regex:__ A JSON string describing a regular expression that a string
should conform to.
+ __arg.keys:__ A JSON object containing any of the above which is used to
describe the kind of data that should be used for generating keys for
spoofed maps.

The following schemas support the following annotations:

### Primitives

#### null
+ arg.options (although there can only be one option)

#### boolean
+ arg.options

#### int
+ arg.options
+ arg.range

#### long
+ arg.options
+ arg.range

#### float
+ arg.options
+ arg.range

#### double
+ arg.options
+ arg.range

#### bytes
+ arg.options
+ arg.length

#### string
+ arg.options
+ arg.length*
+ arg.regex*

__*Note:__ If both arg.length and arg.regex are specified for a string,
the length property (if a JSON number) becomes a minimum length for the
string

### Complex

#### array
+ arg.options
+ arg.length

#### enum
+ arg.options

#### fixed
+ arg.options

#### map
+ arg.options
+ arg.length
+ arg.keys

#### record
+ arg.options

#### union
+ arg.options

### Example schemas

Example schemas are provided in the test/schemas directory. Here are a
few of them:

#### enum.json

```
{
  "name": "enum_comp",
  "type": "enum",
  "symbols": ["PRELUDE", "ALLEMANDE", "COURANTE", "SARABANDE", "MINUET", "BOURREE", "GAVOTTE", "GIGUE"]
}
```

A non-annotated schema. The resulting output will just be a random
enum chosen from the symbols list.

### regex.json
```
{
  "type": "record",
  "name": "regex_test",
  "fields":
    [
      {
        "name": "no_length_property",
        "type":
          {
            "type": "string",
            "arg.regex": "[a-zA-Z]{5,15}",
          }
      },
      {
        "name": "number_length_property",
        "type":
          {
            "type": "string",
            "arg.regex": "[a-zA-Z]*",
            "arg.length": 10
          }
      },
      {
        "name": "min_length_property",
        "type":
          {
            "type": "string",
            "arg.regex": "[a-zA-Z]{0,15}",
            "arg.length":
              {
                "min": 5
              }
          }
      },
      {
        "name": "max_length_property",
        "type":
          {
            "type": "string",
            "arg.regex": "[a-zA-Z]{5,}",
            "arg.length":
              {
                "max": 16
              }
          }
      },
      {
        "name": "min_max_length_property",
        "type":
          {
            "type": "string",
            "arg.regex": "[a-zA-Z]*",
            "arg.length":
              {
                "min": 5,
                "max": 16
              }
          }
      }
    ]
}
```

An annotated record schema, with a variety of string fields. Each field
has its own way of preventing the specified string from becoming too
long, either via the arg.length annotation or the arg.regex annotation.

### options-file.json

```
{
  "type": "record",
  "name": "sentence",
  "fields": [
    {
      "name": "The",
      "type": {
        "type": "string",
        "arg.options": [
          "The"
        ]
      }
    },
    {
      "name": "noun",
      "type": {
        "type": "string",
        "arg.options": {
          "file": "test/schemas/nouns-list.json",
          "encoding": "json"
        }
      }
    },
    {
      "name": "is",
      "type": {
        "type": "string",
        "arg.options": [
          "is",
          "was",
          "will be",
          "is being",
          "was being",
          "has been",
          "had been",
          "will have been"
        ]
      }
    },
    {
      "name": "degree",
      "type": {
        "type": "string",
        "arg.options": [
          "not at all",
          "slightly",
          "somewhat",
          "kind of",
          "pretty",
          "very",
          "entirely"
        ]
      }
    },
    {
      "name": "adjective",
      "type": {
        "type": "string",
        "arg.options": {
          "file": "test/schemas/adjectives-list.json",
          "encoding": "json"
        }
      }
    }
  ]
}

```

A record schema that draws its content from two files, 'nouns-list.json'
and 'adjectives-list.json' to construct a primitive sentence. The script
must be run from the repository base directory in order for this schema
to work with it properly due, to the relative paths of the files.

### options.json

```
{
  "type": "record",
  "name": "options_test_record",
  "fields": [
    {
      "name": "array_field",
      "type": {
        "type": "array",
        "items": "string",
        "arg.options": [
          [
            "Hello",
            "world"
          ],
          [
            "Goodbye",
            "world"
          ],
          [
            "We",
            "meet",
            "again",
            "world"
          ]
        ]
      }
    },
    {
      "name": "enum_field",
      "type": {
        "type": "enum",
        "name": "enum_test",
        "symbols": [
          "HELLO",
          "HI_THERE",
          "GREETINGS",
          "SALUTATIONS",
          "GOODBYE"
        ],
        "arg.options": [
          "HELLO",
          "SALUTATIONS"
        ]
      }
    },
    {
      "name": "fixed_field",
      "type": {
        "type": "fixed",
        "name": "fixed_test",
        "size": 2,
        "arg.options": [
          "\u0034\u0032",
          "\u0045\u0045"
        ]
      }
    },
    {
      "name": "map_field",
      "type": {
        "type": "map",
        "values": "int",
        "arg.options": [
          {
            "zero": 0
          },
          {
            "one": 1,
            "two": 2
          },
          {
            "three": 3,
            "four": 4,
            "five": 5
          },
          {
            "six": 6,
            "seven": 7,
            "eight": 8,
            "nine": 9
          }
        ]
      }
    },
    {
      "name": "map_key_field",
      "type": {
        "type": "map",
        "values": {
          "type": "int",
          "arg.options": [
            -1,
            0,
            1
          ]
        },
        "arg.length": 10,
        "arg.keys": {
          "arg.options": [
            "negative",
            "zero",
            "positive"
          ]
        }
      }
    },
    {
      "name": "record_field",
      "type": {
        "type": "record",
        "name": "record_test",
        "fields": [
          {
            "name": "month",
            "type": "string"
          },
          {
            "name": "day",
            "type": "int"
          }
        ],
        "arg.options": [
          {
            "month": "January",
            "day": 2
          },
          {
            "month": "NANuary",
            "day": 0
          }
        ]
      }
    },
    {
      "name": "union_field",
      "type": [
        "null",
        {
          "type": "boolean",
          "arg.options": [
            true
          ]
        },
        {
          "type": "int",
          "arg.options": [
            42
          ]
        },
        {
          "type": "long",
          "arg.options": [
            4242424242424242
          ]
        },
        {
          "type": "float",
          "arg.options": [
            42.42
          ]
        },
        {
          "type": "double",
          "arg.options": [
            42424242.42424242
          ]
        },
        {
          "type": "bytes",
          "arg.options": [
            "NDI="
          ]
        },
        {
          "type": "string",
          "arg.options": [
            "Forty-two"
          ]
        }
      ]
    }
  ]
}
```

A schema where every field is annotated with an example usage of the
arg.options annotation, as well as an example of the arg.keys
annotation.

