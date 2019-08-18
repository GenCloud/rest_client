package org.restclient.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Pair<K, V> implements Serializable {
	private K key;
	private V value;
}
