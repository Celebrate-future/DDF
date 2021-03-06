% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/clustering.R, R/flink-ddf.R, R/spark-ddf.R
\docType{methods}
\name{ml.kmeans}
\alias{ml.kmeans}
\alias{ml.kmeans,FlinkDDF-method}
\alias{ml.kmeans,SparkDDF-method}
\title{ML KMeans}
\usage{
ml.kmeans(x, ...)

\S4method{ml.kmeans}{FlinkDDF}(x, centers = 2, runs = 5, maxIters = 10)

\S4method{ml.kmeans}{SparkDDF}(x, centers = 2, runs = 5, maxIters = 10)
}
\arguments{
\item{x}{a Distributed Data Frame.}

\item{centers}{number of cluster to be clustered.}

\item{runs}{number of runs.}

\item{maxIters}{max times of iterations.}
}
\value{
a KMeanModel object
}
\description{
Train a Kmeans algorithm

Kmeans using Flink ML's Kmeans

Kmeans using Spark mllib's Kmeans
}

