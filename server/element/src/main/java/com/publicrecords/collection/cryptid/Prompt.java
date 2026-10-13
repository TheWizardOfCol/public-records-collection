package com.publicrecords.collection.cryptid;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Prompt {
  @JsonProperty public final String id;
  @JsonProperty public final String text;
  public Prompt(String id, String text) { this.id=id; this.text=text; }
}
