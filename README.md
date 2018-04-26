As of April 2018, this project has migrated to https://github.com/confluentinc/avro-random-generator
and all development on the original repository owned by C0urante has since ceased.

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
or a regular expression that the string should adhere to. These
annotations are specified inside the schema that Arg spoofs, as parts of
a JSON object with an attribute name of "arg.properties".

These annotations are specified as JSON properties in the schema that
Arg spoofs. They should not collide with any existing properties, or
cause any issues if present when the schema is used with other programs.

## Building

```
$ ./gradlew standalone
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

Currently on Chris Egerton's public GitHub:
https://github.com/C0urante/avro-random-generator
</pre>


## Schema annotations

### Annotation types

The following annotations are currently supported:
+ __options:__ Either a JSON array of possibilities that the data for
spoofing this schema should come from, or a JSON object that conforms to
the following format: `{"file": <file>, "encoding": <encoding>}` (both
fields must be specified). If given as an object, a list of data will be
read from the file after decoding with the specified format (currently
"json" and "binary" are the only supported values, and "binary" may be
somewhat buggy).
+ __iteration:__ A JSON object that conforms to the following format:
`{"start": <start>, "restart": <restart>, "step": <step>}` ("start" has
to be specified, but "restart" and "step" do not). If provided with a
numeric schema, ensures that the first generated value will be equal to
&lt;start&gt;, and successive values will increase by &lt;step&gt;,
wrapping around at &lt;restart&gt;; &lt;step&gt; will default to 1 if
&lt;restart&gt; is greater than &lt;step&gt;, will default to -1 if
&lt;restart&gt; is less than &lt;step&gt;, and an error will be thrown
if &lt;restart&gt; is equal to &lt;step&gt;. If provided with a boolean
schema, only &lt;start&gt; may be specified; the resulting values will
begin with &lt;start&gt; and alternate from `true` to `false` and from
`false` to `true` from that point on.
+ __range:__ A JSON object that conforms to the following format:
`{"min": <min>, "max": <max>}` (at least one of "min" or "max" must be
specified). If provided, ensures that the generated number will be
greater than or equal to &lt;min&gt; and/or strictly less than &lt;max&gt;.
+ __length:__ Either a JSON number or a JSON object that conforms to the
following format: `{"min": <min>, "max": <max>}` (at least one of "min"
or "max" must be specified, and if present, values for either must be
numbers). __Defaults to `{"min": 8, "max": 16}`__.
+ __regex:__ A JSON string describing a regular expression that a string
should conform to.
+ __keys:__ A JSON object containing any of the above which is used to
describe the kind of data that should be used for generating keys for
spoofed maps.
+ __odds:__ A JSON float between 0.0 and 1.0 that, when specified with
a boolean schema, specifies the likelihood that the generated value is
`true`.

The following schemas support the following annotations:

### Primitives

#### null
+ options (although there can only be one option)

#### boolean
+ options
+ iteration
+ odds

#### int
+ options
+ range
+ iteration

#### long
+ options
+ range
+ iteration

#### float
+ options
+ range
+ iteration

#### double
+ options
+ range
+ iteration

#### bytes
+ options
+ length

#### string
+ options
+ length*
+ regex*

__*Note:__ If both length and regex are specified for a string,
the length property (if a JSON number) becomes a minimum length for the
string

### Complex

#### array
+ options
+ length

#### enum
+ options

#### fixed
+ options

#### map
+ options
+ length
+ keys

#### record
+ options

#### union
+ options

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
            "arg.properties": {
              "regex": "[a-zA-Z]{5,15}"
            }
          }
      },
      {
        "name": "number_length_property",
        "type":
          {
            "type": "string",
            "arg.properties": {
              "regex": "[a-zA-Z]*",
              "length": 10
            }
          }
      },
      {
        "name": "min_length_property",
        "type":
          {
            "type": "string",
            "arg.properties": {
              "regex": "[a-zA-Z]{0,15}",
              "length":
                {
                  "min": 5
                }
            }
          }
      },
      {
        "name": "max_length_property",
        "type":
          {
            "type": "string",
            "arg.properties": {
              "regex": "[a-zA-Z]{5,}",
              "length":
                {
                  "max": 16
                }
            }
          }
      },
      {
        "name": "min_max_length_property",
        "type":
          {
            "type": "string",
            "arg.properties": {
              "regex": "[a-zA-Z]*",
              "length":
                {
                  "min": 5,
                  "max": 16
                }
            }
          }
      }
    ]
}
```

An annotated record schema, with a variety of string fields. Each field
has its own way of preventing the specified string from becoming too
long, either via the length annotation or the regex annotation.

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
          "arg.properties": {
            "options": [
              "The"
            ]
          }
      }
    },
    {
      "name": "noun",
      "type": {
        "type": "string",
        "arg.properties": {
          "options": {
            "file": "test/schemas/nouns-list.json",
            "encoding": "json"
          }
        }
      }
    },
    {
      "name": "is",
      "type": {
        "type": "string",
        "arg.properties": {
          "options": [
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
      }
    },
    {
      "name": "degree",
      "type": {
        "type": "string",
        "arg.properties": {
          "options": [
            "not at all",
            "slightly",
            "somewhat",
            "kind of",
            "pretty",
            "very",
            "entirely"
          ]
        }
      }
    },
    {
      "name": "adjective",
      "type": {
        "type": "string",
        "arg.properties": {
          "options": {
            "file": "test/schemas/adjectives-list.json",
            "encoding": "json"
          }
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
        "arg.properties":
          {
            "options": [
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
        "arg.properties":
          {
            "options": [
              "HELLO",
              "SALUTATIONS"
            ]
          }
      }
    },
    {
      "name": "fixed_field",
      "type": {
        "type": "fixed",
        "name": "fixed_test",
        "size": 2,
        "arg.properties":
          {
            "options": [
              "\u0034\u0032",
              "\u0045\u0045"
            ]
          }
      }
    },
    {
      "name": "map_field",
      "type": {
        "type": "map",
        "values": "int",
        "arg.properties":
          {
            "options": [
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
      }
    },
    {
      "name": "map_key_field",
      "type": {
        "type": "map",
        "values": {
          "type": "int",
          "arg.properties": {
            "options": [
              -1,
              0,
              1
            ]
          }
        },
        "arg.properties": {
          "length": 10,
          "keys": {
            "options": [
              "negative",
              "zero",
              "positive"
            ]
          }
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
        "arg.properties": {
          "options": [
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
      }
    },
    {
      "name": "union_field",
      "type": [
        "null",
        {
          "type": "boolean",
          "arg.properties": {
            "options": [
              true
            ]
          }
        },
        {
          "type": "int",
          "arg.properties": {
            "options": [
              42
            ]
          }
        },
        {
          "type": "long",
          "arg.properties": {
            "options": [
              4242424242424242
            ]
          }
        },
        {
          "type": "float",
          "arg.properties": {
            "options": [
              42.42
            ]
          }
        },
        {
          "type": "double",
          "arg.properties": {
            "options": [
              42424242.42424242
            ]
          }
        },
        {
          "type": "bytes",
          "arg.properties": {
            "options": [
              "NDI="
            ]
          }
        },
        {
          "type": "string",
          "arg.properties": {
            "options": [
              "Forty-two"
            ]
          }
        }
      ]
    }
  ]
}
```

A schema where every field is annotated with an example usage of the
options annotation, as well as an example of the keys annotation.

