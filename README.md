# LSRepair
Live Search of Fix Ingredients for Automated Program Repair

Description
----------------
A tool of automated program repair using fix ingredients at method declaration level. 
It search three kinds of similar methods from real-world code bases:
- **Signature-similar** methods: which are methods that have the same *return type*, *method name* and *parameter types*. We consider that code reuse (e.g., copy/paste) and implementation of basic routines (e.g., string equality) are pervasive in software projects. Thus, it is possible to discover two implementations of the same functionality with slight differences representing corner-cases addressing defects.
- **Syntactially similar** methods: which are methods, searched by a code clone detection technique, that are syntactically similar to the buggy methods.
- **Semantically similar** methods: which are methods, searched by a code clone detection technique, that are semantcially similar to the buggy methods.<br>
The detailes are presented in following paper if you are interested in this work.

```
@inproceedings{liu2018lsrepair,
  Author = {Liu, Kui and Koyuncu, Anil and Kim, Kisub and Kim, Dongsun and F. Bissyand{\'e}, Tegawend{\'e}},
  Title = {{LSRepair}: Live Search of Fix Ingredients for Automated Program Repair},
  Booktitle = {Proceedings of the 25th Asia-Pacific Software Engineering Conference},
  Year = {2018},
  address = {Nara, Japan}
}
```

 
