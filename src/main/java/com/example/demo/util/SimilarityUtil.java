package com.example.demo.util;

import java.util.List;

public class SimilarityUtil {
	// Trong com.example.demo.util.SimilarityUtil
	public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
	    double dotProduct = 0.0;
	    double normA = 0.0;
	    double normB = 0.0;
	    for (int i = 0; i < vectorA.length; i++) {
	        dotProduct += vectorA[i] * vectorB[i];
	        normA += Math.pow(vectorA[i], 2);
	        normB += Math.pow(vectorB[i], 2);
	    }
	    if (normA == 0 || normB == 0) {
	        return 0.0;
	    }
	    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	}
}