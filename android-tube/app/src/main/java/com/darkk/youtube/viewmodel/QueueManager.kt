package com.darkk.youtube.viewmodel

import com.darkk.youtube.innertube.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QueueManager {
    private val _queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queue: StateFlow<List<VideoItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    fun playNext(video: VideoItem) {
        val current = _queue.value.toMutableList()
        val index = if (_currentIndex.value == -1) 0 else _currentIndex.value + 1
        current.add(index, video)
        _queue.value = current
    }

    fun addToQueue(video: VideoItem) {
        val current = _queue.value.toMutableList()
        current.add(video)
        _queue.value = current
        if (_currentIndex.value == -1) {
            _currentIndex.value = 0
        }
    }

    fun removeFromQueue(index: Int) {
        val current = _queue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _queue.value = current
            
            if (_currentIndex.value >= index) {
                _currentIndex.value = maxOf(0, _currentIndex.value - 1)
            }
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
    }

    fun getNextVideo(): VideoItem? {
        val current = _queue.value
        val index = _currentIndex.value + 1
        if (index in current.indices) {
            _currentIndex.value = index
            return current[index]
        }
        return null
    }
    
    fun hasNext(): Boolean {
        return _currentIndex.value + 1 < _queue.value.size
    }
}
